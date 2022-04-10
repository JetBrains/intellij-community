// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift

import com.intellij.java.ift.JavaBasedLangSupport
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator
import org.jetbrains.kotlin.idea.configuration.createConfigureKotlinNotificationCollector
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import training.project.ProjectUtils
import java.nio.file.Path

class KotlinLangSupport : JavaBasedLangSupport() {
    override val contentRootDirectoryName = "KotlinLearningProject"

    override val primaryLanguage: String = "kotlin"
    override val scratchFileName: String = "Learning.kt"

    private val sourcesDirectoryName = "src"

    override val sampleFilePath: String = "$sourcesDirectoryName/Sample.kt"

    override fun installAndOpenLearningProject(
        contentRoot: Path,
        projectToClose: Project?,
        postInitCallback: (learnProject: Project) -> Unit
    ) {
        super.installAndOpenLearningProject(contentRoot, projectToClose) { project ->
            // It is required to not run SetupJavaProjectFromSourcesActivity because
            // it will overwrite our manual setup of KotlinJavaRuntime library
            project.putUserData(PlatformProjectOpenProcessor.PROJECT_OPENED_BY_PLATFORM_PROCESSOR, false)

            setupKotlin(project)
            postInitCallback(project)
        }
    }

    private fun setupKotlin(project: Project) {
        @Suppress("DialogTitleCapitalization")
        runBackgroundableTask(KotlinLessonsBundle.message("configure.kotlin.progress.title"), project, false) {
            KotlinSdkType.setUpIfNeeded()
            val configurator = KotlinJavaModuleConfigurator.instance
            val collector = createConfigureKotlinNotificationCollector(project)
            invokeAndWaitIfNeeded {
                configurator.getOrCreateKotlinLibrary(project, collector)
            }
            val writeActions = mutableListOf<() -> Unit>()
            runReadAction {
                val module = ModuleManager.getInstance(project).modules.firstOrNull()
                    ?: error("Failed to find module in learning project")
                configurator.configureModule(module, collector, writeActions)
            }
            invokeLater { writeActions.forEach { it() } }
        }
    }

    override fun applyToProjectAfterConfigure(): (Project) -> Unit = { project ->
        super.applyToProjectAfterConfigure().invoke(project)
        ProjectCodeStyleImporter.apply(project, KotlinStyleGuideCodeStyle.INSTANCE)
        invokeLater { ProjectUtils.markDirectoryAsSourcesRoot(project, sourcesDirectoryName) }
    }
}