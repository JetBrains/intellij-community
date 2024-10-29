// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
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
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinGradleCompatibilityStore
import org.jetbrains.plugins.gradle.settings.GradleSettings

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

    override fun isApplicable(module: Module): Boolean {
        // We should not configure buildSrc modules as they belong to a different subproject and define convention plugins.
        return module.buildSystemType == BuildSystemType.Gradle &&
                !module.name.contains("buildSrc")
    }

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
        configureAndGetConfiguredModules(project, excludeModules)
    }

    override fun queueSyncIfNeeded(project: Project) {
        KotlinProjectConfigurationService.getInstance(project).queueSync()
    }

    override suspend fun queueSyncAndWaitForProjectToBeConfigured(project: Project) {
        blockingContextScope {
            queueSyncIfNeeded(project)
        }
    }

    @JvmSuppressWildcards
    override fun configureAndGetConfiguredModules(project: Project, excludeModules: Collection<Module>): Set<Module> {
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())

        dialog.show()
        if (!dialog.isOK) return emptySet()
        val kotlinVersion = dialog.kotlinVersion ?: return emptySet()

        KotlinJ2KOnboardingFUSCollector.logStartConfigureKt(project)
        val commandKey = "command.name.configure.kotlin"
        val result = runWithModalProgressBlocking(project, KotlinIdeaGradleBundle.message(commandKey)) {
            configureSilently(
              project = project,
              modules = dialog.modulesToConfigure,
              kotlinVersionsAndModules = dialog.versionsAndModules,
              version = IdeKotlinVersion.get(kotlinVersion),
              modulesAndJvmTargets = dialog.modulesAndJvmTargets,
              commandKey = commandKey
            )
        }

        for (file in result.changedFiles.getChangedFiles()) {
            OpenFileAction.openFile(file.virtualFile, project)
        }

        KotlinAutoConfigurationNotificationHolder.getInstance(project).onManualConfigurationCompleted()
        result.collector.showNotification()

        return result.configuredModules
    }

    private fun Project.isGradleSyncPending(module: Module): Boolean {
        return KotlinProjectConfigurationService.getInstance(this).isSyncDesired(module)
    }

    private fun Project.isGradleSyncInProgress(): Boolean {
        return KotlinProjectConfigurationService.getInstance(this).isSyncInProgress()
    }

    private fun calculateAutoConfigSettingsReadAction(module: Module): AutoConfigurationSettings? {
        val project = module.project
        val baseModule = module.toModuleGroup().baseModule

        // The buildSrc folder is used to define convention plugins, which can be incredibly complex.
        // So do not allow auto-configuration of any such projects.
        if (module.project.modules.any { it.name.contains("buildSrc") }) return null
        if (!isAutoConfigurationEnabled() || !isApplicable(baseModule)) return null
        if (project.isGradleSyncPending(module) || project.isGradleSyncInProgress()) return null

        if (module.hasKotlinPluginEnabled() || baseModule.getBuildScriptPsiFile() == null) return null

        val gradleVersion = project.guessProjectDir()?.path?.let {
            val linkedSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(it)
            linkedSettings?.resolveGradleVersion()
        } ?: return null

        val hierarchy = project.buildKotlinModuleHierarchy()
        val moduleNode = hierarchy?.getNodeForModule(baseModule) ?: return null
        if (moduleNode.definedKotlinVersion != null || moduleNode.hasKotlinVersionConflict()) return null

        val forcedKotlinVersion = moduleNode.getForcedKotlinVersion()
        val allConfigurableKotlinVersions = getAllConfigurableKotlinVersions()
        if (forcedKotlinVersion != null && !allConfigurableKotlinVersions.contains(forcedKotlinVersion)) {
            return null
        }

        val possibleKotlinVersionsToUse = if (forcedKotlinVersion != null) {
            listOf(forcedKotlinVersion)
        } else allConfigurableKotlinVersions

        val remainingKotlinVersions = possibleKotlinVersionsToUse
            .filter { baseModule.kotlinSupportsJvmTarget(it) }
            .filter { KotlinGradleCompatibilityStore.kotlinVersionSupportsGradle(it, gradleVersion) }

        val maxKotlinVersion = remainingKotlinVersions.maxOrNull() ?: return null

        return AutoConfigurationSettings(baseModule, maxKotlinVersion)
    }

    override suspend fun calculateAutoConfigSettings(module: Module): AutoConfigurationSettings? {
        return readAction {
            calculateAutoConfigSettingsReadAction(module)
        }
    }

    override suspend fun runAutoConfig(settings: AutoConfigurationSettings) {
        val module = settings.module
        val project = module.project
        val moduleVersions = readAction {
            getKotlinVersionsAndModules(project, this).first
        }
        val jvmTargets = readAction {
            checkModuleJvmTargetCompatibility(listOf(module), settings.kotlinVersion).moduleJvmTargets
        }
        KotlinJ2KOnboardingFUSCollector.logStartConfigureKt(project, true)
        val commandKey = "command.name.configure.kotlin.automatically"
        val result = withModalProgress(project, KotlinIdeaGradleBundle.message(commandKey)) {
            configureSilently(
              project = module.project,
              modules = listOf(module),
              kotlinVersionsAndModules = moduleVersions,
              version = settings.kotlinVersion,
              modulesAndJvmTargets = jvmTargets,
              commandKey = "command.name.configure.kotlin.automatically",
              isAutoConfig = true
            )
        }

        KotlinAutoConfigurationNotificationHolder.getInstance(project)
          .showAutoConfiguredNotification(module.name, result.changedFiles.calculateChanges())
    }

    private class ConfigurationResult(
        val collector: NotificationMessageCollector,
        val configuredModules: Set<Module>,
        val changedFiles: ChangedConfiguratorFiles
    )

    private fun addUndoListener(project: Project, modules: List<Module>, isAutoConfig: Boolean) {
        // Auto-config only ever works on a single module
        val firstModule = modules.firstOrNull()
        UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
            override fun undo() {
                if (isAutoConfig && firstModule != null) {
                    queueSyncIfNeeded(project)
                    KotlinAutoConfigurationNotificationHolder.getInstance(project)
                        .showAutoConfigurationUndoneNotification(firstModule)
                }
                KotlinJ2KOnboardingFUSCollector.logConfigureKtUndone(project)
            }

            override fun redo() {
                if (isAutoConfig && firstModule != null) {
                    queueSyncIfNeeded(project)
                    KotlinAutoConfigurationNotificationHolder.getInstance(project).reshowAutoConfiguredNotification(firstModule)
                }
            }
        })
    }

    // Expected to be called from a coroutine with a progress reporter
    private suspend fun configureSilently(
        project: Project,
        modules: List<Module>,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        version: IdeKotlinVersion,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm>,
        commandKey: String,
        isAutoConfig: Boolean = false
    ): ConfigurationResult = reportSequentialProgress { reporter ->
        reporter.nextStep(endFraction = 30, KotlinIdeaGradleBundle.message("step.configure.kotlin.preparing"))
        readAndWriteAction {
            val collector = NotificationMessageCollector.create(project)

            // First check all the files and abort if something would not work
            val configureAction =
                createConfigureWithVersionAction(project, modules, version, collector, kotlinVersionsAndModules, modulesAndJvmTargets)
            // Now that everything has been read and verified, apply the changes
            writeAction {
                reporter.nextStep(endFraction = 100, KotlinIdeaGradleBundle.message("step.configure.kotlin.writing"))
                project.executeCommand(KotlinIdeaGradleBundle.message(commandKey)) {
                    val (configuredModules, changedFiles) = configureAction()
                    val firstModule = modules.firstOrNull()
                    if (isAutoConfig && firstModule != null) {
                        queueSyncIfNeeded(project)
                    }
                    addUndoListener(project, modules, isAutoConfig)
                    ConfigurationResult(collector, configuredModules, changedFiles)
                }
            }
        }
    }

    private fun createConfigureWithVersionAction(
        project: Project,
        modulesToConfigure: List<Module>,
        kotlinVersion: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm> = emptyMap()
    ): () -> Pair<Set<Module>, ChangedConfiguratorFiles> {
        val configuredModules = mutableSetOf<Module>()
        val changedFiles = ChangedConfiguratorFiles()
        val topLevelBuildScript = project.getTopLevelBuildScriptPsiFile()
        val modulesWithTheSameKotlin = kotlinVersionsAndModules[kotlinVersion.artifactVersion]
        val modulesToRemoveKotlinVersion = mutableListOf<Module>()
        // Remove version from modules with the same version as the version to configure:
        modulesWithTheSameKotlin?.values?.let { modulesToRemoveKotlinVersion.addAll(it) }

        val writeActions = mutableListOf<() -> Unit>()

        val rootModule = getRootModule(project)
        val definedVersionInPluginSettings = rootModule?.let { getPluginManagementVersion(it) }
        var addVersionToModuleBuildScript = definedVersionInPluginSettings?.parsedVersion != kotlinVersion

        if (rootModule != null) {
            val allKotlinModules = kotlinVersionsAndModules.values.flatMap { it.values }
            val hasDefinedVersion = kotlinVersionsAndModules.filter { it.key != kotlinVersion.artifactVersion }.isNotEmpty()
            val kotlinVersionDefinedExplicitlyEverywhere = allKotlinModules.all { module ->
                module.getBuildScriptPsiFile()?.let {
                    GradleBuildScriptSupport.getManipulator(it)
                }?.hasExplicitlyDefinedKotlinVersion() == true
            }
            val addVersionToSettings: Boolean
            // If there are different Kotlin versions in the project, don't add to settings
            if (hasDefinedVersion || definedVersionInPluginSettings != null || !kotlinVersionDefinedExplicitlyEverywhere) {
                addVersionToSettings = false
            } else {
                // If we have any version in the root module, don't need to add the version to the settings file
                addVersionToSettings = !kotlinVersionsAndModules.values.flatMap { it.keys }.contains(rootModule.name)
            }
            if (addVersionToSettings) {
                rootModule.getBuildScriptSettingsPsiFile()?.takeIf { it.canBeConfigured() }?.let {
                    writeActions.add {
                        if (configureSettingsFile(it, kotlinVersion, changedFiles)) {
                            // This happens only for JVM, not for Android
                            addVersionToModuleBuildScript = false
                        }
                    }
                }
            }
            if (topLevelBuildScript != null) {
                // rootModule is just <PROJECT_NAME>, but we need <PROJECT_NAME>.main:
                val rootModuleName = rootModule.name
                val firstSourceRootModule = modulesWithTheSameKotlin?.get(rootModuleName)
                firstSourceRootModule?.let {
                    // We don't cut a Kotlin version from a top build script
                    modulesToRemoveKotlinVersion.remove(firstSourceRootModule)
                    addVersionToModuleBuildScript = false
                }

                val jvmTarget = if (modulesAndJvmTargets.isNotEmpty()) {
                    modulesAndJvmTargets[rootModuleName]
                } else {
                    getTargetBytecodeVersionFromModule(rootModule, kotlinVersion)
                }
                if (topLevelBuildScript.canBeConfigured()) {
                    writeActions.add {
                        val configured = configureModule(
                            rootModule,
                            topLevelBuildScript,
                            /* isTopLevelProjectFile = true is needed only for KotlinAndroidGradleModuleConfigurator that overrides
                    addElementsToFiles()*/
                            isTopLevelProjectFile = true,
                            kotlinVersion,
                            jvmTarget,
                            collector,
                            changedFiles,
                            addVersionToModuleBuildScript
                        )
                        if (configured) configuredModules.add(rootModule)
                    }

                    if (modulesToConfigure.contains(rootModule)) {
                        writeActions.add {
                            val configured = configureModule(
                                rootModule,
                                topLevelBuildScript,
                                false,
                                kotlinVersion,
                                jvmTarget,
                                collector,
                                changedFiles,
                                addVersionToModuleBuildScript
                            )
                            if (configured) configuredModules.add(rootModule)
                            // If Kotlin version wasn't added to settings.gradle, then it has just been added to root script
                            addVersionToModuleBuildScript = false
                        }
                    }
                } else {
                    showErrorMessage(
                        project,
                        KotlinIdeaGradleBundle.message("error.text.cannot.find.build.gradle.file.for.module", rootModule.name)
                    )
                    return { Pair(configuredModules, changedFiles) }
                }
            }
        }

        for (module in modulesToRemoveKotlinVersion) {
            module.getBuildScriptPsiFile()?.let {
                writeActions.add {
                    removeKotlinVersionFromBuildScript(it, changedFiles)
                }
            }
        }

        for (module in modulesToConfigure) {
            val file = module.getBuildScriptPsiFile()
            if (file != null && file.canBeConfigured()) {
                if (file == topLevelBuildScript) { // We configured the root module separately above
                    continue
                }
                val jvmTarget = if (modulesAndJvmTargets.isNotEmpty()) {
                    modulesAndJvmTargets[module.name]
                } else {
                    getTargetBytecodeVersionFromModule(module, kotlinVersion)
                }
                writeActions.add {
                    val configured = configureModule(
                        module = module,
                        file = file,
                        isTopLevelProjectFile = false,
                        ideKotlinVersion = kotlinVersion,
                        jvmTarget = jvmTarget,
                        collector = collector,
                        changedFiles = changedFiles,
                        addVersion = addVersionToModuleBuildScript
                    )
                    if (configured) configuredModules.add(module)
                }
            } else {
                showErrorMessage(
                    project,
                    KotlinIdeaGradleBundle.message("error.text.cannot.find.build.gradle.file.for.module", module.name)
                )
                return { Pair(configuredModules, changedFiles) }
            }
        }
        for (file in changedFiles.getChangedFiles()) {
            file.virtualFile?.let {
                collector.addMessage(KotlinIdeaGradleBundle.message("text.was.modified", it.path))
            }
        }
        return {
            writeActions.forEach { it.invoke() }
            Pair(configuredModules, changedFiles)
        }
    }

    // We only keep this for backwards-compatibility with the android configurator and tests
    @Deprecated("Should be replaced with a read/writeAction invoking createConfigureWithVersionAction")
    fun configureWithVersion(
        project: Project,
        modulesToConfigure: List<Module>,
        kotlinVersion: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm> = emptyMap()
    ): Pair<Set<Module>, ChangedConfiguratorFiles> {
        return createConfigureWithVersionAction(
            project = project,
            modulesToConfigure = modulesToConfigure,
            kotlinVersion = kotlinVersion,
            collector = collector,
            kotlinVersionsAndModules = kotlinVersionsAndModules,
            modulesAndJvmTargets = modulesAndJvmTargets
        ).invoke()
    }

    private fun removeKotlinVersionFromBuildScript(
        file: PsiFile,
        changedFiles: ChangedConfiguratorFiles
    ) {
        file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
            changedFiles.storeOriginalFileContent(file)
            GradleBuildScriptSupport.getManipulator(file).findAndRemoveKotlinVersionFromBuildScript()
            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(file)
        }
    }

    /**
     * Returns true if the module was configured.
     */
    open fun configureModule(
        module: Module,
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        ideKotlinVersion: IdeKotlinVersion,
        jvmTarget: String?,
        collector: NotificationMessageCollector,
        changedFiles: ChangedConfiguratorFiles,
        addVersion: Boolean = true
    ): Boolean {
        return configureBuildScripts(file, isTopLevelProjectFile, ideKotlinVersion, jvmTarget, changedFiles, addVersion)
    }

    private fun configureBuildScripts(
        file: PsiFile,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        jvmTarget: String?,
        changedFiles: ChangedConfiguratorFiles
    ) {
        val sdk = ModuleUtil.findModuleForPsiElement(file)?.let { ModuleRootManager.getInstance(it).sdk }
        GradleBuildScriptSupport.getManipulator(file).configureBuildScripts(
            kotlinPluginName,
            getKotlinPluginExpression(file.isKtDsl()),
            getStdlibArtifactName(sdk, version),
            addVersion,
            version,
            jvmTarget,
            changedFiles
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
        addVersion: Boolean = true,
        changedBuildFiles: ChangedConfiguratorFiles
    ) {
        if (!isTopLevelProjectFile) { // isTopLevelProjectFile = true is needed only for Android
            changedBuildFiles.storeOriginalFileContent(file)
            GradleBuildScriptSupport.getManipulator(file).configureProjectBuildScript(kotlinPluginName, version)
            configureBuildScripts(file, addVersion, version, jvmTarget, changedBuildFiles)
        }
    }

    /**
     * Returns true if build scripts were configured.
     */
    private fun configureBuildScripts(
        file: PsiFile,
        isTopLevelProjectFile: Boolean,
        version: IdeKotlinVersion,
        jvmTarget: String?,
        changedFiles: ChangedConfiguratorFiles,
        addVersion: Boolean = true
    ): Boolean {
        return file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), null) {
            addElementsToFiles(file, isTopLevelProjectFile, version, jvmTarget, addVersion, changedFiles)

            for (changedFile in changedFiles.getChangedFiles()) {
                CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(changedFile)
            }
            return@executeWriteCommand changedFiles.getChangedFiles().isNotEmpty()
        }
    }

    protected open fun configureSettingsFile(
        file: PsiFile,
        version: IdeKotlinVersion,
        changedFiles: ChangedConfiguratorFiles
    ): Boolean {
        return file.project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", file.name), groupId = null) {
            changedFiles.storeOriginalFileContent(file)
            val isModified = GradleBuildScriptSupport.getManipulator(file)
                .configureSettingsFile(getKotlinPluginExpression(file.isKtDsl()), version)

            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(file)
            isModified
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

    @Deprecated(
        "Please implement/use the KotlinBuildSystemDependencyManager EP instead.",
        replaceWith = ReplaceWith("KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)?.addDependency(module, library.withScope(scope))")
    )
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

        private fun getAllConfigurableKotlinVersions(): List<IdeKotlinVersion> {
            return KotlinGradleCompatibilityStore.allKotlinVersions()
        }

        /**
         * Currently, returns true if this module has a jvmTarget >= 8.
         * If a future Kotlin version requires a higher jvmTarget, then it will be required for that [kotlinVersion].
         */
        private fun Module.kotlinSupportsJvmTarget(kotlinVersion: IdeKotlinVersion): Boolean {
            val jvmTarget = getTargetBytecodeVersionFromModule(this, kotlinVersion) ?: return false
            val jvmTargetNum = getJvmTargetNumber(jvmTarget) ?: return false
            return jvmTargetNum >= 8
        }

        /**
         * Returns the best Kotlin version that can be used for a new child of the [parentModule],
         * or null if there is a version conflict and no version can be used without issues.
         */
        fun findBestKotlinVersion(parentModule: Module, gradleVersion: GradleVersion): IdeKotlinVersion? {
            val project = parentModule.project
            val hierarchy = project.buildKotlinModuleHierarchy()
            val parentModuleNode = hierarchy?.getNodeForModule(parentModule) ?: return null
            if (parentModuleNode.hasKotlinVersionConflict()) return null

            val forcedKotlinVersion = parentModuleNode.getForcedKotlinVersion()
            val allConfigurableKotlinVersions = getAllConfigurableKotlinVersions()
            if (forcedKotlinVersion != null) return forcedKotlinVersion

            val remainingKotlinVersions = allConfigurableKotlinVersions
                .filter { parentModule.kotlinSupportsJvmTarget(it) }
                .filter { KotlinGradleCompatibilityStore.kotlinVersionSupportsGradle(it, gradleVersion) }

            return remainingKotlinVersions.maxOrNull()
        }

        /**
         * Returns the defined Kotlin version in the pluginManagement block in the settings.gradle file for the [module].
         * Returns null if the version is not defined in the settings.gradle file.
         * Returns a non-null value, but null version inside the object, if the version was defined but could not be parsed.
         */
        fun getPluginManagementVersion(module: Module): DefinedKotlinPluginManagementVersion? {
            return module.getBuildScriptSettingsPsiFile()?.let {
                GradleBuildScriptSupport.getManipulator(it)
                    .findKotlinPluginManagementVersion()
            }
        }

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
            if (!buildScript.canBeConfigured()) {
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
            ?.takeIf { it.canBeConfigured() }
            ?.let {
                it.project.executeWriteCommand(KotlinIdeaGradleBundle.message("change.build.gradle.configuration"), null) { body(it) }
            }

        fun getKotlinStdlibVersion(module: Module): String? = module.getBuildScriptPsiFile()?.let {
            GradleBuildScriptSupport.getManipulator(it).getKotlinStdlibVersion()
        }

        private fun showErrorMessage(project: Project, @Nls message: String?) {
            Messages.showErrorDialog(
                project,
                "<html>" + KotlinIdeaGradleBundle.message("text.couldn.t.configure.kotlin.gradle.plugin.automatically") + "<br/>" +
                        (if (message != null) "$message<br/>" else "") +
                        "<br/>${KotlinIdeaGradleBundle.message("text.see.manual.installation.instructions")}</html>",
                KotlinIdeaGradleBundle.message("title.configure.kotlin.gradle.plugin")
            )
        }

        fun isAutoConfigurationEnabled(): Boolean = Registry.`is`("kotlin.configuration.gradle.autoConfig.enabled", true)
    }
}
