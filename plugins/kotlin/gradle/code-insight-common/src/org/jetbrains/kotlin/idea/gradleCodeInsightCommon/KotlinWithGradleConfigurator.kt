// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.extensions.gradle.KotlinGradleConstants.GRADLE_PLUGIN_ID
import org.jetbrains.kotlin.idea.extensions.gradle.KotlinGradleConstants.GROUP_ID
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersionOrDefault
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.projectConfiguration.getJvmStdlibArtifactId
import org.jetbrains.kotlin.idea.quickfix.AbstractChangeFeatureSupportLevelFix
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile

abstract class KotlinWithGradleConfigurator : KotlinProjectConfigurator {

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        val module = moduleSourceRootGroup.baseModule
        if (!isApplicable(module)) {
            return ConfigureKotlinStatus.NON_APPLICABLE
        }

        if (moduleSourceRootGroup.sourceRootModules.all(Module::hasKotlinPluginEnabled)) {
            return ConfigureKotlinStatus.CONFIGURED
        }

        val (projectBuildFile, topLevelBuildFile) = runReadAction {
            module.getBuildScriptPsiFile() to module.project.getTopLevelBuildScriptPsiFile()
        }

        if (projectBuildFile == null && topLevelBuildFile == null) {
            return ConfigureKotlinStatus.NON_APPLICABLE
        }

        if (projectBuildFile?.isConfiguredByAnyGradleConfigurator() == true) {
            return ConfigureKotlinStatus.BROKEN
        }

        return ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }

    private fun PsiFile.isConfiguredByAnyGradleConfigurator(): Boolean {
        @Suppress("DEPRECATION")
        val extensions = Extensions.getExtensions(KotlinProjectConfigurator.EP_NAME)

        return extensions
            .filterIsInstance<KotlinWithGradleConfigurator>()
            .any { it.isFileConfigured(this) }
    }

    protected open fun isApplicable(module: Module): Boolean =
        module.buildSystemType == BuildSystemType.Gradle

    protected open fun getMinimumSupportedVersion() = "1.0.0"

    protected fun PsiFile.isKtDsl() = this is KtFile

    private fun isFileConfigured(buildScript: PsiFile): Boolean {
        val manipulator = GradleBuildScriptSupport.findManipulator(buildScript) ?: return false
        return with(manipulator) {
            isConfiguredWithOldSyntax(kotlinPluginName) || isConfigured(getKotlinPluginExpression(buildScript.isKtDsl()))
        }
    }

    @JvmSuppressWildcards
    override fun configure(project: Project, excludeModules: Collection<Module>) {
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())

        dialog.show()
        if (!dialog.isOK) return

        val collector = configureSilently(project, dialog.modulesToConfigure, IdeKotlinVersion.get(dialog.kotlinVersion))
        collector.showNotification()
    }

    private fun configureSilently(project: Project, modules: List<Module>, version: IdeKotlinVersion): NotificationMessageCollector {
        return project.executeCommand(KotlinIdeaGradleBundle.message("command.name.configure.kotlin")) {
            val collector = NotificationMessageCollector.create(project)
            val changedFiles = configureWithVersion(project, modules, version, collector)

            for (file in changedFiles) {
                OpenFileAction.openFile(file.virtualFile, project)
            }
            collector
        }
    }

    fun configureWithVersion(
        project: Project,
        modulesToConfigure: List<Module>,
        kotlinVersion: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        useJDK1_6forTests: Boolean = false
    ): HashSet<PsiFile> {
        val filesToOpen = HashSet<PsiFile>()
        val topLevelBuildScript = project.getTopLevelBuildScriptPsiFile()
        var addVersionToModuleBuildScript = true
        if (topLevelBuildScript != null) {
            if (canConfigureFile(topLevelBuildScript)) {
                configureBuildScripts(topLevelBuildScript, true, kotlinVersion, collector, filesToOpen, true, useJDK1_6forTests)
            }
            addVersionToModuleBuildScript = !GradleBuildScriptSupport.getManipulator(topLevelBuildScript).isKotlinConfiguredInBuildScript()
        }

        for (module in modulesToConfigure) {
            val file = module.getBuildScriptPsiFile()
            if (file != null && canConfigureFile(file)) {
                if (file == topLevelBuildScript) {
                    configureModule(module, file, false, kotlinVersion, collector, filesToOpen, true, useJDK1_6forTests)
                    addVersionToModuleBuildScript = false // Just added to root script, no need to add to other scripts
                } else {
                    configureModule(module, file, false, kotlinVersion, collector, filesToOpen, addVersionToModuleBuildScript, useJDK1_6forTests)
                }
            } else {
                showErrorMessage(
                    project,
                    KotlinIdeaGradleBundle.message("error.text.cannot.find.build.gradle.file.for.module", module.name)
                )
            }
        }
        return filesToOpen
    }

    open fun configureModule(
        module: Module,
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        filesToOpen: MutableCollection<PsiFile>,
        addVersion: Boolean = true,
        useJDK1_6forTests: Boolean = false
    ) {
        configureBuildScripts(file, isTopLevelProjectFile, version, collector, filesToOpen, addVersion, useJDK1_6forTests)
    }

    private fun configureBuildScripts(
        file: PsiFile,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        useJDK1_6forTests: Boolean = false
    ): ChangedFiles {
        val sdk = ModuleUtil.findModuleForPsiElement(file)?.let { ModuleRootManager.getInstance(it).sdk }
        val jvmTarget = if (useJDK1_6forTests) {
            JvmTarget.JVM_1_6.description
        } else {
            getJvmTarget(sdk, version)
        }
        return GradleBuildScriptSupport.getManipulator(file).configureBuildScripts(
            kotlinPluginName,
            getKotlinPluginExpression(file.isKtDsl()),
            getStdlibArtifactName(sdk, version),
            addVersion,
            version,
            jvmTarget
        )
    }

    protected open fun getStdlibArtifactName(sdk: Sdk?, version: IdeKotlinVersion) = getJvmStdlibArtifactId(sdk, version)

    protected open fun getJvmTarget(sdk: Sdk?, version: IdeKotlinVersion): String? = null

    protected abstract val kotlinPluginName: String
    protected abstract fun getKotlinPluginExpression(forKotlinDsl: Boolean): String

    protected open fun addElementsToFiles(
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        version: IdeKotlinVersion,
        addVersion: Boolean = true,
        useJDK1_6forTests: Boolean = false
    ): ChangedFiles {
        if (!isTopLevelProjectFile) {
            val wasModified = GradleBuildScriptSupport.getManipulator(file).configureProjectBuildScript(kotlinPluginName, version)
            val changedFiles = configureBuildScripts(file, addVersion, version, useJDK1_6forTests)
            if (wasModified) changedFiles.add(file)
            return changedFiles
        } else return HashSet()
    }

    private fun configureBuildScripts(
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        filesToOpen: MutableCollection<PsiFile>,
        addVersion: Boolean = true,
        useJDK1_6forTests: Boolean = false
    ) {
        file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
            val changedFiles = addElementsToFiles(file, isTopLevelProjectFile, version, addVersion, useJDK1_6forTests)

            for (changedFile in changedFiles) {
                CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(changedFile)
                changedFile.virtualFile?.let {
                    collector.addMessage(KotlinIdeaGradleBundle.message("text.was.modified", it.path))
                }
            }
            filesToOpen.addAll(changedFiles)
        }
    }

    override fun updateLanguageVersion(
        module: Module,
        languageVersion: String?,
        apiVersion: String?,
        requiredStdlibVersion: ApiVersion,
        forTests: Boolean
    ) {
        val runtimeUpdateRequired = getRuntimeLibraryVersion(module)?.apiVersion?.let { runtimeVersion ->
            runtimeVersion < requiredStdlibVersion
        } ?: false

        if (runtimeUpdateRequired) {
            Messages.showErrorDialog(
                module.project,
                KotlinIdeaGradleBundle.message("error.text.this.language.feature.requires.version", requiredStdlibVersion),
                KotlinIdeaGradleBundle.message("title.update.language.version")
            )
            return
        }

        val element = changeLanguageVersion(module, languageVersion, apiVersion, forTests)

        element?.let {
            OpenFileDescriptor(module.project, it.containingFile.virtualFile, it.textRange.startOffset).navigate(true)
        }
    }

    override fun changeGeneralFeatureConfiguration(
        module: Module,
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    ) {
        val sinceVersion = feature.sinceApiVersion

        if (state != LanguageFeature.State.DISABLED && getRuntimeLibraryVersionOrDefault(module).apiVersion < sinceVersion) {
            Messages.showErrorDialog(
                module.project,
                KotlinIdeaGradleBundle.message("error.text.support.requires.version", feature.presentableName, sinceVersion),
                AbstractChangeFeatureSupportLevelFix.getFixText(state, feature.presentableName)
            )
            return
        }

        val element = changeFeatureConfiguration(module, feature, state, forTests)
        if (element != null) {
            OpenFileDescriptor(module.project, element.containingFile.virtualFile, element.textRange.startOffset).navigate(true)
        }
    }

    override fun addLibraryDependency(
        module: Module,
        element: PsiElement,
        library: ExternalLibraryDescriptor,
        libraryJarDescriptor: LibraryJarDescriptor,
        scope: DependencyScope
    ) {
        val scope = OrderEntryFix.suggestScopeByLocation(module, element)
        addKotlinLibraryToModule(module, scope, library)
    }

    companion object {
        @NonNls
        const val CLASSPATH = "classpath \"$GROUP_ID:$GRADLE_PLUGIN_ID:\$kotlin_version\""

        fun getGroovyDependencySnippet(artifactName: String, scope: String, withVersion: Boolean, gradleVersion: GradleVersion): String {
            val updatedScope = gradleVersion.scope(scope)
            val versionStr = if (withVersion) ":\$kotlin_version" else ""

            return "$updatedScope \"org.jetbrains.kotlin:$artifactName$versionStr\""
        }

        fun getGroovyApplyPluginDirective(pluginName: String) = "apply plugin: '$pluginName'"

        fun addKotlinLibraryToModule(module: Module, scope: DependencyScope, libraryDescriptor: ExternalLibraryDescriptor) {
            val buildScript = module.getBuildScriptPsiFile() ?: return
            if (!canConfigureFile(buildScript)) {
                return
            }

            GradleBuildScriptSupport.getManipulator(buildScript).addKotlinLibraryToModuleBuildScript(module, scope, libraryDescriptor)

            buildScript.virtualFile?.let {
                NotificationMessageCollector.create(buildScript.project)
                    .addMessage(KotlinIdeaGradleBundle.message("text.was.modified", it.path))
                    .showNotification()
            }
        }

        fun changeFeatureConfiguration(
            module: Module,
            feature: LanguageFeature,
            state: LanguageFeature.State,
            forTests: Boolean
        ) = changeBuildGradle(module) {
            GradleBuildScriptSupport.getManipulator(it).changeLanguageFeatureConfiguration(feature, state, forTests)
        }

        fun changeLanguageVersion(module: Module, languageVersion: String?, apiVersion: String?, forTests: Boolean) =
            changeBuildGradle(module) { buildScriptFile ->
                val manipulator = GradleBuildScriptSupport.getManipulator(buildScriptFile)
                var result: PsiElement? = null
                if (languageVersion != null) {
                    result = manipulator.changeLanguageVersion(languageVersion, forTests)
                }

                if (apiVersion != null) {
                    result = manipulator.changeApiVersion(apiVersion, forTests)
                }

                result
            }

        private fun changeBuildGradle(module: Module, body: (PsiFile) -> PsiElement?): PsiElement? = module.getBuildScriptPsiFile()
            ?.takeIf { canConfigureFile(it) }
            ?.let {
                it.project.executeWriteCommand(KotlinIdeaGradleBundle.message("change.build.gradle.configuration"), null) { body(it) }
            }

        fun getKotlinStdlibVersion(module: Module): String? = module.getBuildScriptPsiFile()?.let {
            GradleBuildScriptSupport.getManipulator(it).getKotlinStdlibVersion()
        }

        private fun canConfigureFile(file: PsiFile): Boolean = WritingAccessProvider.isPotentiallyWritable(file.virtualFile, null)

        private fun showErrorMessage(project: Project, @Nls message: String?) {
            Messages.showErrorDialog(
                project,
                "<html>" + KotlinIdeaGradleBundle.message("text.couldn.t.configure.kotlin.gradle.plugin.automatically") + "<br/>" +
                        (if (message != null) "$message<br/>" else "") +
                        "<br/>${KotlinIdeaGradleBundle.message("text.see.manual.installation.instructions")}</html>",
                KotlinIdeaGradleBundle.message("title.configure.kotlin.gradle.plugin")
            )
        }
    }
}
