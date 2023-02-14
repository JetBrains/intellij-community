// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.kotlin.gradle.newTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

internal object LinkedProjectPathsTestsFeature : TestFeature<LinkedProjectPaths> {
    override fun createDefaultConfiguration(): LinkedProjectPaths = LinkedProjectPaths(mutableSetOf())

    override fun KotlinMppTestsContext.beforeImport() {
        testConfiguration.getConfiguration(LinkedProjectPathsTestsFeature).linkedProjectPaths.forEach {
            GradleProjectsLinker.linkGradleProject(it, testProjectRoot, testProject)
        }
    }
}

class LinkedProjectPaths(val linkedProjectPaths: MutableSet<String>)

interface GradleProjectsLinkingDsl {
    fun TestConfigurationDslScope.linkProject(projectPath: String) {
        writeAccess.getConfiguration(LinkedProjectPathsTestsFeature).linkedProjectPaths.add(projectPath)
    }
}

object GradleProjectsLinker {
    // copied from GradleImportingTestCase.GRADLE_JDK_NAME
    private val GRADLE_JDK_NAME = "Gradle JDK"

    fun linkGradleProject(relativeProjectPath: String, projectPath: File, project: Project) {
        val absoluteProjectPath = projectPath.resolve(relativeProjectPath).absolutePath
        val localFileSystem = LocalFileSystem.getInstance()
        val projectFile = localFileSystem.refreshAndFindFileByPath(absoluteProjectPath)
            ?: error("Failed to find projectFile: $absoluteProjectPath")

        val settings = createLinkSettings(projectFile.toNioPath(), project).apply {
            gradleJvm = GRADLE_JDK_NAME
        }

        ExternalSystemUtil.linkExternalProject(
            /* externalSystemId = */ GradleConstants.SYSTEM_ID,
            /* projectSettings = */ settings,
            /* project = */ project,
            /* importResultCallback = */ null,
            /* isPreviewMode = */ false,
            /* progressExecutionMode = */ ProgressExecutionMode.MODAL_SYNC
        )
    }
}
