// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import training.lang.LangSupport
import java.io.File
import java.io.FileFilter
import java.nio.file.Path

class KotlinLangSupport : LangSupport {
    override val primaryLanguage: String = "kotlin"
    override val filename: String = "Learning.kt"

    override val contentRootDirectoryName: String
        get() = TODO("Not yet implemented")

    override fun installAndOpenLearningProject(
        contentRoot: Path,
        projectToClose: Project?,
        postInitCallback: (learnProject: Project) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun copyLearningProjectFiles(projectDirectory: File, destinationFilter: FileFilter?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getSdkForProject(project: Project, selectedSdk: Sdk?): Sdk? {
        TODO("Not yet implemented")
    }

    override fun applyProjectSdk(sdk: Sdk, project: Project) {
        TODO("Not yet implemented")
    }

    override fun applyToProjectAfterConfigure(): (Project) -> Unit {
        TODO("Not yet implemented")
    }

    override fun checkSdk(sdk: Sdk?, project: Project) {
        TODO("Not yet implemented")
    }

    override fun getProjectFilePath(projectName: String): String {
        TODO("Not yet implemented")
    }
}