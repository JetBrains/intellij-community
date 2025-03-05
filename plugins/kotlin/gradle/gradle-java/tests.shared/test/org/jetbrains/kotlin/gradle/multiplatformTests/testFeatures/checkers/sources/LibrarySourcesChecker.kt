// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContextImpl
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinTestProperties
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.findMostSpecificExistingFileOrNewDefault
import java.io.File
import kotlin.collections.component2

data class LibrarySourcesConfiguration(
    var classifier: String? = null
)

interface LibrarySourcesChecker : TestFeature<LibrarySourcesConfiguration>, LibrarySourcesCheckDsl {
    companion object : AbstractTestChecker<LibrarySourcesConfiguration>() {
        override fun createDefaultConfiguration() = LibrarySourcesConfiguration()

        override fun KotlinMppTestsContext.check() {

            val configuration = testConfiguration.getConfiguration(this@Companion)
            val classifier = configuration.classifier ?: return
            val testProperties =
                (this as? KotlinMppTestsContextImpl)?.testProperties ?: error("LibrarySourcesChecker requires KotlinMppTestsContextImpl")

            val expectedDownloadedSourcesFile = findMostSpecificExistingFileOrNewDefault(classifier)

            val sourcesContent =
                testProperties.substituteKotlinTestPropertiesInText(expectedDownloadedSourcesFile.readText(), expectedDownloadedSourcesFile)
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { it.trim() }

            val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(testProject)
            val libraries = libraryTable.libraries

            if (libraries.size != sourcesContent.size) {
                error(
                    "Imported libraries count (${libraries.size}) does not match the number of source entries (${sourcesContent.size}).\n"
                            + "Imported libraries: ${libraries.joinToString { it.name.toString() }} \n"
                            + "Imported libraries files: ${
                        libraries.joinToString {
                            it.getFiles(OrderRootType.SOURCES).joinToString { it.path }
                        }
                    }"
                )
            }

            for (sourceEntry in sourcesContent) {
                val (libraryName, expectedSourcesPath) = parseSourceEntry(sourceEntry)

                val library = libraries.find { it.name?.contains(libraryName) == true }
                    ?: error("Library not found: $libraryName")

                val foundSourceVirtualFiles = library.getFiles(OrderRootType.SOURCES).map { it.path }

                val foundSourceVirtualFilesContainExpected =
                    foundSourceVirtualFiles.any { it.removeSuffix("/").endsWith(expectedSourcesPath) }

                if (!foundSourceVirtualFilesContainExpected) {
                    error(
                        "For $libraryName found sources do not contain expected. " +
                                "Found sources: ${foundSourceVirtualFiles}. " +
                                "Expected sources: $expectedSourcesPath."
                    )
                }
            }
        }

        private fun parseSourceEntry(entry: String): Pair<String, String> {
            val (libraryName, sourcesJarPath) = entry.split(" ", limit = 2)
            return libraryName to sourcesJarPath
        }
    }
}
