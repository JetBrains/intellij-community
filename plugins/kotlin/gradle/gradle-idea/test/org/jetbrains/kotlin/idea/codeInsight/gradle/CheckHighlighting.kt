// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.codeMetaInfo.CodeMetaInfoTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.findCorrespondingFileInTestDir
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File
import java.nio.file.Paths

data class HighlightingCheck(
    private val project: Project,
    private val projectPath: String,
    private val testDataDirectory: File,
    private val testLineMarkers: Boolean = true,
    private val severityLevel: HighlightSeverity = HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
    private val correspondingFilePostfix: String = ""
) {

    private val checker = CodeMetaInfoTestCase(
        codeMetaInfoTypes = listOfNotNull(
            HighlightingConfiguration(
                renderTextAttributesKey = false,
                severityLevel = severityLevel,
                checkNoError = false
            ),
            if (testLineMarkers) LineMarkerConfiguration() else null
        ),
        checkNoDiagnosticError = false
    )


    fun invokeOnAllModules() {
        ModuleManager.getInstance(project).modules.forEach { module ->
            this(module)
        }
    }

    operator fun invoke(module: Module) {
        for (sourceRoot in module.sourceRoots) {
            VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                if (file.isDirectory || file.extension != "kt" && file.extension != "java") return@processFilesRecursively true
                runInEdtAndWait {
                    checker.checkFile(
                        file,
                        file.findCorrespondingFileInTestDir(Paths.get(projectPath), testDataDirectory, correspondingFilePostfix),
                        project
                    )
                }
                true
            }
        }
    }
}
