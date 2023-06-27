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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.util.module
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
import org.jetbrains.plugins.gradle.util.GradleUtil

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

        val collector = configureSilently(
            project,
            dialog.modulesToConfigure,
            dialog.versionsAndModules,
            IdeKotlinVersion.get(dialog.kotlinVersion),
            dialog.modulesAndJvmTargets
        )
        collector.showNotification()
    }

    private fun configureSilently(
        project: Project,
        modules: List<Module>,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        version: IdeKotlinVersion,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm>
    ): NotificationMessageCollector {
        return project.executeCommand(KotlinIdeaGradleBundle.message("command.name.configure.kotlin")) {
            val collector = NotificationMessageCollector.create(project)
            val changedFiles = configureWithVersion(project, modules, version, collector, kotlinVersionsAndModules, modulesAndJvmTargets)

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
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm> = emptyMap()
    ): HashSet<PsiFile> {
        val filesToOpen = HashSet<PsiFile>()
        val topLevelBuildScript = project.getTopLevelBuildScriptPsiFile()
        var addVersionToModuleBuildScript = true
        val modulesWithTheSameKotlin = kotlinVersionsAndModules[kotlinVersion.artifactVersion]
        val modulesToRemoveKotlinVersion = mutableListOf<Module>()
        // Remove version from modules with the same version as the version to configure:
        modulesWithTheSameKotlin?.values?.let { modulesToRemoveKotlinVersion.addAll(it) }
        val rootModule: Module?
        if (topLevelBuildScript != null) {
            rootModule = topLevelBuildScript.module
            if (rootModule != null) {
                // rootModule is just <PROJECT_NAME>, but we need <PROJECT_NAME>.main:
                val rootModuleName = rootModule.name
                val firstSourceRootNodule = modulesWithTheSameKotlin?.get(rootModuleName)
                firstSourceRootNodule?.let {
                    // We don't cut a Kotlin version from a top build script
                    modulesToRemoveKotlinVersion.remove(firstSourceRootNodule)
                    addVersionToModuleBuildScript = false
                }
                // If we have any version in the root nodule, don't need to add the version to the settings file
                val addVersionToSettings = !kotlinVersionsAndModules.values.flatMap { it.keys }.contains(rootModule.name)
                if (addVersionToSettings) {
                    rootModule.getBuildScriptSettingsPsiFile()?.let {
                        if (configureSettingsFile(it, kotlinVersion, filesToOpen)) { // This happens only for JVM, not for Android
                            addVersionToModuleBuildScript = false
                        }
                    }
                }

                val jvmTarget = if (modulesAndJvmTargets.isNotEmpty()) {
                    modulesAndJvmTargets[rootModuleName]
                } else {
                    getTargetBytecodeVersionFromModule(rootModule, kotlinVersion)
                }
                if (canConfigureFile(topLevelBuildScript)) {
                    configureModule(
                        rootModule,
                        topLevelBuildScript,
                        /* isTopLevelProjectFile = true is needed only for KotlinAndroidGradleModuleConfigurator that overrides
                addElementsToFiles()*/
                        isTopLevelProjectFile = true,
                        kotlinVersion,
                        jvmTarget,
                        collector,
                        filesToOpen,
                        addVersionToModuleBuildScript
                    )

                    if (modulesToConfigure.contains(rootModule)) {
                        configureModule(
                            rootModule,
                            topLevelBuildScript,
                            false,
                            kotlinVersion,
                            jvmTarget,
                            collector,
                            filesToOpen,
                            addVersionToModuleBuildScript
                        )
                        // If Kotlin version wasn't added to settings.gradle, then it has just been added to root script
                        addVersionToModuleBuildScript = false
                    }
                } else {
                    showErrorMessage(
                        project,
                        KotlinIdeaGradleBundle.message("error.text.cannot.find.build.gradle.file.for.module", rootModule.name)
                    )
                }
            }
        }

        for (module in modulesToRemoveKotlinVersion) {
            module.getBuildScriptPsiFile()?.let {
                removeKotlinVersionFromBuildScript(it, filesToOpen)
            }
        }

        for (module in modulesToConfigure) {
            val file = module.getBuildScriptPsiFile()
            if (file != null && canConfigureFile(file)) {
                if (file == topLevelBuildScript) { // We configured the root module separately above
                    continue
                }
                val jvmTarget = if (modulesAndJvmTargets.isNotEmpty()) {
                    modulesAndJvmTargets[module.name]
                } else {
                    getTargetBytecodeVersionFromModule(module, kotlinVersion)
                }
                configureModule(module, file, false, kotlinVersion, jvmTarget, collector, filesToOpen, addVersionToModuleBuildScript)
            } else {
                showErrorMessage(
                    project,
                    KotlinIdeaGradleBundle.message("error.text.cannot.find.build.gradle.file.for.module", module.name)
                )
            }
        }
        for (file in filesToOpen) {
            file.virtualFile?.let {
                collector.addMessage(KotlinIdeaGradleBundle.message("text.was.modified", it.path))
            }
        }
        return filesToOpen
    }

    private fun removeKotlinVersionFromBuildScript(
        file: PsiFile,
        filesToOpen: MutableCollection<PsiFile>
    ) {
        val isModified = file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
            val isModified = GradleBuildScriptSupport.getManipulator(file).findAndRemoveKotlinVersionFromBuildScript()

            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(file)
            isModified
        }

        val virtualFile = file.virtualFile
        if (virtualFile != null && isModified) {
            filesToOpen.add(file)
        }
    }

    open fun configureModule(
        module: Module,
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        ideKotlinVersion: IdeKotlinVersion,
        jvmTarget: String?,
        collector: NotificationMessageCollector,
        filesToOpen: MutableCollection<PsiFile>,
        addVersion: Boolean = true
    ) {
        configureBuildScripts(file, isTopLevelProjectFile, ideKotlinVersion, jvmTarget, filesToOpen, addVersion)
    }

    private fun configureBuildScripts(
        file: PsiFile,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        jvmTarget: String?
    ): ChangedFiles {
        val sdk = ModuleUtil.findModuleForPsiElement(file)?.let { ModuleRootManager.getInstance(it).sdk }
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
        jvmTarget: String?,
        addVersion: Boolean = true
    ): ChangedFiles {
        return if (!isTopLevelProjectFile) { // isTopLevelProjectFile = true is needed only for Android
            val wasModified = GradleBuildScriptSupport.getManipulator(file).configureProjectBuildScript(kotlinPluginName, version)
            val changedFiles = configureBuildScripts(file, addVersion, version, jvmTarget)
            if (wasModified) changedFiles.add(file)
            changedFiles
        } else HashSet()
    }

    private fun configureBuildScripts(
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        version: IdeKotlinVersion,
        jvmTarget: String?,
        filesToOpen: MutableCollection<PsiFile>,
        addVersion: Boolean = true
    ) {
        file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
            val changedFiles = addElementsToFiles(file, isTopLevelProjectFile, version, jvmTarget, addVersion)

            for (changedFile in changedFiles) {
                CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(changedFile)
            }
            filesToOpen.addAll(changedFiles)
        }
    }

    protected open fun configureSettingsFile(
        file: PsiFile,
        version: IdeKotlinVersion,
        filesToOpen: MutableCollection<PsiFile>
    ): Boolean {
        val isModified = file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
            val isModified = GradleBuildScriptSupport.getManipulator(file)
                .configureSettingsFile(getKotlinPluginExpression(file.isKtDsl()), version)

            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(file)
            isModified
        }

        val virtualFile = file.virtualFile
        if (virtualFile != null && isModified) {
            filesToOpen.add(file)
            return true
        }
        return false
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

        fun getGroovyDependencySnippet(
            artifactName: String,
            scope: String,
            withVersion: Boolean,
            gradleVersion: GradleVersion
        ): String {
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

        fun isAutoConfigurationEnabled(): Boolean {
            return Registry.`is`("kotlin.configuration.gradle.autoConfig.enabled", false)
        }
    }
}
