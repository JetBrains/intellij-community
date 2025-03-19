// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess
import java.io.File

object AllFilesAreUnderContentRootChecker : TestFeature<AllFilesUnderContentRootCheckConfiguration> {
    override fun createDefaultConfiguration() = AllFilesUnderContentRootCheckConfiguration()

    override fun KotlinMppTestsContext.afterImport() {
        if (testConfiguration.getConfiguration(AllFilesAreUnderContentRootChecker).isDisabled) return

        val files = mutableListOf<File>()

        testProjectRoot.walk()
            .onEnter { it.isDirectory && it.name != "build" } // skip build directory, there might be some generated sources there
            .filter { it.isFile && (it.extension == "kt" ||  it.extension == "java") }
            .forEach {
            runReadAction {
                val vFile = LocalFileSystem.getInstance().findFileByIoFile(it)
                    ?: error("Can't find VirtualFile for IO File ${it.canonicalPath}")

                if (!ProjectFileIndex.getInstance(testProject).isInSource(vFile)) files += it
            }
        }

        require(files.isEmpty()) {
            "Error: the following files are not in sources of the test project:\n" +
                    files.joinToString { it.relativeTo(testProjectRoot).path } + "\n" +
                    "\n" +
                    "The most common reasons:\n" +
                    "   - gradle project isn't included (typo in settings.gradle.kts?)\n" +
                    "   - such source set doesn't exist (mistake in targets configuration or source sets hierarchy)"
        }
    }
}

data class AllFilesUnderContentRootCheckConfiguration(var isDisabled: Boolean = false)

interface AllFilesUnderContentRootConfigurationDsl {
    var TestConfigurationDslScope.allowFilesNotUnderContentRoot: Boolean
        get() = config.isDisabled
        set(value) { config.isDisabled = value }
}

private val TestConfigurationDslScope.config: AllFilesUnderContentRootCheckConfiguration
    get() = writeAccess.getConfiguration(AllFilesAreUnderContentRootChecker)
