// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.allopen.AllOpenPluginNames
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.ConfigurationResultBuilder
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion.Companion.defaultKotlinVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptManipulator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.PathUtil

abstract class AbstractGradleKotlinCompilerPluginProjectConfigurator : KotlinCompilerPluginProjectConfigurator {
    override fun isApplicable(module: Module): Boolean =
        module.project.getTopLevelBuildScriptPsiFile() != null

    override fun configureModule(module: Module, configurationResultBuilder: ConfigurationResultBuilder) {
        val project = module.project
        val topLevelFile = project.getTopLevelBuildScriptPsiFile() ?: return
        val moduleFile = module.getBuildScriptPsiFile().takeIf { it != topLevelFile }

        project.executeWriteCommand(KotlinIdeaGradleBundle.message("command.name.configure.0", topLevelFile.name), null) {
            topLevelFile.add(addVersion = true, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
            topLevelFile.addCustomization(addVersion = true, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
            moduleFile?.add(addVersion = false, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
            moduleFile?.addCustomization(addVersion = false, sourceModule = module, changedFiles = configurationResultBuilder.changedFiles)
            configurationResultBuilder.configuredModule(module)
        }
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
            PathUtil.KOTLIN_JAVA_STDLIB_NAME,
            addVersion = addVersion,
            version = version,
            jvmTarget = null,
            changedFiles = changedFiles
        )
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

class JpaGradleKotlinCompilerPluginProjectConfigurator : AbstractGradleKotlinCompilerPluginProjectConfigurator() {
    override val kotlinCompilerPluginId: String = "jpa"

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"plugin.jpa\")" else "id \"org.jetbrains.kotlin.plugin.jpa\""

    override fun PsiFile.addCustomization(addVersion: Boolean, sourceModule: Module, changedFiles: ChangedConfiguratorFiles) {
        val (manipulator, version) = manipulatorAndVersion(sourceModule)
        if (version.kotlinVersion.isAtLeast(2, 3, 20)) return

        manipulator.configureBuildScripts(
            "kotlin.$kotlinCompilerPluginId",
            "kotlin(\"plugin.allopen\")".takeIf { this is KtFile } ?: "id \"org.jetbrains.kotlin.plugin.allopen\"",
            PathUtil.KOTLIN_JAVA_STDLIB_NAME,
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