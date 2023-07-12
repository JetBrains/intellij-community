// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.LibrarySourceRoot
import org.jetbrains.kotlin.idea.codeMetaInfo.CodeMetaInfoTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.findCorrespondingFileInTestDir
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertTrue

data class HighlightingCheck(
    private val project: Project,
    private val projectPath: String,
    private val testDataDirectory: File,
    private val testLineMarkers: Boolean = true,
    private val severityLevel: HighlightSeverity = HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
    private val correspondingFilePostfix: String = "",
    private val postprocessActualTestData: (String) -> String = { it }
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
        val modules = ModuleManager.getInstance(project).modules.toList()
        assertTrue(modules.isNotEmpty(), "Expected at least one module")
        modules.combineMultipleFailures(this::invoke)
    }

    operator fun invoke(module: Module) = combineMultipleFailures {
        for (sourceRoot in module.sourceRoots) {
            VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                if (file.isIrrelevant())
                    return@processFilesRecursively true
                runInEdtAndWait {
                    runAssertion {
                        checker.checkFile(
                            file,
                            file.findCorrespondingFileInTestDir(Paths.get(projectPath), testDataDirectory, correspondingFilePostfix),
                            project,
                            postprocessActualTestData
                        )
                    }
                }
                true
            }
        }
    }

    fun invokeOnLibraries(librarySourceRoots: Map<String, LibrarySourceRoot>) {
        val libraryWithTestData = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.mapNotNull { library ->
            val sourceRoot = librarySourceRoots[library.name] ?: return@mapNotNull null
            library to sourceRoot.testDataPath
        }
        assertTrue(libraryWithTestData.isNotEmpty(), "Expected at least one library")

        libraryWithTestData.combineMultipleFailures { (library, testDataPath) ->
            combineMultipleFailures {
                for (sourceRoot in library.getFiles(OrderRootType.SOURCES)) {
                    VfsUtilCore.processFilesRecursively(sourceRoot) { file ->
                        invokeOnLibraryFile(sourceRoot, file, testDataPath)
                        true
                    }
                }
            }
        }
    }

    private fun CombineMultipleFailuresContext.invokeOnLibraryFile(sourceRoot: VirtualFile, file: VirtualFile, libraryTestDataPath: String) {
        if (file.isIrrelevant()) {
            return
        }

        fun testDataSearchError(candidate: File? = null): Nothing =
            error("Can't find file in testdata for copied file $file" + candidate?.let { ": checked at path ${it.absolutePath}" }.orEmpty() )

        val relativePathComponents = (VfsUtilCore.findRelativePath(sourceRoot, file, VFS_SEPARATOR_CHAR) ?: testDataSearchError()).split(VFS_SEPARATOR_CHAR)

        val testDataFile = buildList {
            add(testDataDirectory.path)
            add(libraryTestDataPath)
            add("src")
            add(relativePathComponents[0]) // sourceSet
            add("kotlin")
            addAll(relativePathComponents.drop(1))
        }.joinToString(File.separator).let(::File)

        if (!testDataFile.exists()) testDataSearchError(testDataFile)

        runInEdtAndWait {
            runAssertion {
                checker.checkFile(file, testDataFile, project, postprocessActualTestData)
            }
        }
    }

    private fun VirtualFile.isIrrelevant(): Boolean {
        return isDirectory || extension != "kt" && extension != "java" || path.contains("build/generated")
    }
}
