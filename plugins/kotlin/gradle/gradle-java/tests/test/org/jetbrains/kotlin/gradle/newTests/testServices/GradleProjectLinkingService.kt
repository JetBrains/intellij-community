// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object GradleProjectLinkingService {
    fun linkGradleProject(relativeProjectPath: String, projectPath: Path, project: Project) {
        val absoluteProjectPath = projectPath.resolve(relativeProjectPath).absolutePathString()
        val localFileSystem = LocalFileSystem.getInstance()
        val projectFile = localFileSystem.refreshAndFindFileByPath(absoluteProjectPath)
            ?: error("Failed to find projectFile: $absoluteProjectPath")

        val settings = createLinkSettings(projectFile.toNioPath(), project).apply {
            gradleJvm = GradleImportingTestCase.GRADLE_JDK_NAME
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