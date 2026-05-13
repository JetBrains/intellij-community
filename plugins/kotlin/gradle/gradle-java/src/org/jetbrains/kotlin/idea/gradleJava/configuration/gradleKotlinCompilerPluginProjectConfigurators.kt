// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.allopen.AllOpenPluginNames
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.ConfigurationResultBuilder
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion.Companion.defaultKotlinVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptManipulator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractGradleKotlinCompilerPluginProjectConfigurator : KotlinCompilerPluginProjectConfigurator {
    override fun isApplicable(module: Module): Boolean =
        module.project.getTopLevelBuildScriptPsiFile() != null

    override fun configureModule(module: Module, configurationResultBuilder: ConfigurationResultBuilder) {
        val project = module.project
        val topLevelFile = project.getTopLevelBuildScriptPsiFile() ?: return
        val moduleFile = module.getBuildScriptPsiFile().takeIf { it != topLevelFile }

        project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", topLevelFile.name), null) {
            if (moduleFile == null) {
                topLevelFile.add(addVersion = true, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
                topLevelFile.addCustomization(addVersion = true, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
            }
            else {
                val settingsFile = module.getBuildScriptSettingsPsiFile()
                    ?: module.getTopLevelBuildScriptSettingsPsiFile()
                    ?: topLevelFile.findSiblingSettingsFile()
                if (settingsFile != null) {
                    settingsFile.addPluginVersionDeclarations(sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
                }
                else {
                    topLevelFile.addPluginVersionDeclarations(
                        sourceModule = module,
                        changedFiles = configurationResultBuilder.changedFiles,
                        applyFalse = true
                    )
                }
                moduleFile.add(addVersion = false, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
                moduleFile.addCustomization(addVersion = false, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
            }
            configurationResultBuilder.configuredModule(module)
        }
    }

    override fun configureModuleModCommand(module: Module): ModCommand {
        val project = module.project
        val topLevelFile = project.getTopLevelBuildScriptPsiFile() ?: return ModCommand.nop()

        val actionContext = ActionContext.from(null, topLevelFile)
        return ModCommand.psiUpdate(actionContext) { updater ->
            val writablePomFile = updater.getWritable(topLevelFile)
            val changedFiles = ChangedConfiguratorFiles()
            val moduleBuildScriptPsiFile = module.getBuildScriptPsiFile()
            val moduleFile = moduleBuildScriptPsiFile.takeIf { it != topLevelFile }?.let(updater::getWritable)
            if (moduleFile == null) {
                writablePomFile.add(addVersion = true, sourceModule = module, changedFiles = changedFiles)
                writablePomFile.addCustomization(addVersion = true, sourceModule = module, changedFiles = changedFiles)
            }
            else {
                val settingsFile = (module.getBuildScriptSettingsPsiFile()
                    ?: module.getTopLevelBuildScriptSettingsPsiFile()
                    ?: topLevelFile.findSiblingSettingsFile())?.let(updater::getWritable)
                if (settingsFile != null) {
                    settingsFile.addPluginVersionDeclarations(sourceModule = module, changedFiles = changedFiles)
                }
                else {
                    writablePomFile.addPluginVersionDeclarations(sourceModule = module, changedFiles = changedFiles, applyFalse = true)
                }
                moduleFile.add(addVersion = false, sourceModule = module, changedFiles = changedFiles)
                moduleFile.addCustomization(addVersion = false, sourceModule = module, changedFiles = changedFiles)
            }
        }.andThen(KotlinDependencyProvider.syncModCommand(topLevelFile))
    }

    protected fun PsiFile.manipulatorAndVersion(sourceModule: Module): Pair<GradleBuildScriptManipulator<*>, IdeKotlinVersion> {
        val manipulator = GradleBuildScriptSupport.getManipulator(this)

        val version =
            manipulator.getKotlinVersion()
                ?: GradleBuildScriptSupport.findKotlinPluginManagementVersion(sourceModule)?.parsedVersion
                ?: detectKotlinStdlibVersion(sourceModule)
                ?: defaultKotlinVersion
        return manipulator to version
    }

    protected fun PsiFile.add(addVersion: Boolean, sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
        val (manipulator, version) = manipulatorAndVersion(sourceModule)

        manipulator.configureBuildScripts(
            "kotlin.$kotlinCompilerPluginId",
            getKotlinPluginExpression(this is KtFile),
            addVersion = addVersion,
            version = version,
            jvmTarget = null,
            changedFiles = changedFiles
        )
    }

    protected open fun PsiFile.addPluginVersionDeclarations(
        sourceModule: Module,
        changedFiles: ChangedConfiguratorFiles,
        applyFalse: Boolean = false
    ) {
        addPluginVersionDeclaration(getKotlinPluginExpression(this is KtFile), sourceModule, changedFiles, applyFalse)
    }

    protected fun PsiFile.addPluginVersionDeclaration(
        kotlinPluginExpression: String,
        sourceModule: Module,
        changedFiles: ChangedConfiguratorFiles,
        applyFalse: Boolean = false
    ) {
        val (manipulator, version) = manipulatorAndVersion(sourceModule)
        if (applyFalse) {
            manipulator.configurePluginInPluginsGroup(
                kotlinPluginExpression = kotlinPluginExpression,
                addVersion = true,
                version = version,
                applyFalse = true,
                changedFiles = changedFiles
            )
        }
        else {
            changedFiles.storeOriginalFileContent(this)
            manipulator.configureSettingsFile(kotlinPluginExpression, version)
        }
    }

    protected fun PsiFile.findKotlinVersion(sourceModule: Module): IdeKotlinVersion =
        manipulatorAndVersion(sourceModule).second

    private fun PsiFile.findSiblingSettingsFile(): PsiFile? {
        val settingsFile = virtualFile.parent?.findChild("settings.gradle.kts")
            ?: virtualFile.parent?.findChild("settings.gradle")
            ?: return null
        return PsiManager.getInstance(project).findFile(settingsFile)
    }

    protected open fun PsiFile.addCustomization(addVersion: Boolean, sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
    }

    fun detectKotlinStdlibVersion(module: Module): IdeKotlinVersion? {
        val project = module.project
        val fileBasedIndex = FileBasedIndex.getInstance()
        val projectFileIndex = ProjectFileIndex.getInstance(project)

        val stdlibManifests =
            DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                fileBasedIndex
                    .getContainingFilesIterator(
                        KotlinStdlibIndex.NAME,
                        KotlinStdlibIndex.KOTLIN_STDLIB_NAME,
                        GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
                    )
            })

        return runReadAction {
            var stdlibVersion: IdeKotlinVersion? = null
            for (manifest in stdlibManifests) {
                val virtualFile = projectFileIndex.getClassRootForFile(manifest) ?: continue
                KotlinJvmStdlibDetectorFacility.getStdlibVersion(listOf(virtualFile))?.let {
                    // the most recent version wins
                    if (stdlibVersion == null || stdlibVersion < it) {
                        stdlibVersion = it
                    }
                }
            }
            stdlibVersion
        }
    }

    protected abstract fun getKotlinPluginExpression(forKotlinDsl: Boolean): String
}

class SpringGradleKotlinCompilerPluginProjectConfigurator : AbstractGradleKotlinCompilerPluginProjectConfigurator() {
    override val kotlinCompilerPluginId: String = "spring"

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"plugin.spring\")" else "id \"org.jetbrains.kotlin.plugin.spring\""
}

class LombokGradleKotlinCompilerPluginProjectConfigurator : AbstractGradleKotlinCompilerPluginProjectConfigurator() {
    override val kotlinCompilerPluginId: String = "lombok"

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"plugin.lombok\")" else "id \"org.jetbrains.kotlin.plugin.lombok\""

    override fun PsiFile.addCustomization(addVersion: Boolean, sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
        configureKotlinLombokConfigIfNeeded(sourceModule, changedFiles)
        configureKaptForLombokIfNeeded(sourceModule, changedFiles)
    }
}

class JpaGradleKotlinCompilerPluginProjectConfigurator : AbstractGradleKotlinCompilerPluginProjectConfigurator() {
    override val kotlinCompilerPluginId: String = "jpa"

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"plugin.jpa\")" else "id \"org.jetbrains.kotlin.plugin.jpa\""

    override fun PsiFile.addPluginVersionDeclarations(
        sourceModule: Module,
        changedFiles: ChangedConfiguratorFiles,
        applyFalse: Boolean
    ) {
        addPluginVersionDeclaration(getKotlinPluginExpression(this is KtFile), sourceModule, changedFiles, applyFalse)
        val version = findKotlinVersion(sourceModule)
        if (version.kotlinVersion.isAtLeast(2, 3, 20)) return

        addPluginVersionDeclaration(
            "kotlin(\"plugin.allopen\")".takeIf { this is KtFile } ?: "id \"org.jetbrains.kotlin.plugin.allopen\"",
            sourceModule,
            changedFiles,
            applyFalse
        )
    }

    override fun PsiFile.addCustomization(addVersion: Boolean, sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
        val (manipulator, version) = manipulatorAndVersion(sourceModule)
        if (version.kotlinVersion.isAtLeast(2, 3, 20)) return

        manipulator.configureBuildScripts(
            "kotlin.$kotlinCompilerPluginId",
            "kotlin(\"plugin.allopen\")".takeIf { this is KtFile } ?: "id \"org.jetbrains.kotlin.plugin.allopen\"",
            addVersion = addVersion,
            version = version,
            jvmTarget = null,
            changedFiles = changedFiles
        )

        val opts = (AllOpenPluginNames.SUPPORTED_PRESETS[kotlinCompilerPluginId] ?: return).map {
            "${AllOpenPluginNames.ANNOTATION_OPTION_NAME}(\"$it\")"
        }.toTypedArray()

        manipulator.configurePluginOptions("allOpen", changedFiles, *opts)
    }
}