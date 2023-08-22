// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractReferenceResolveWithCrossLibTest : AbstractReferenceResolveWithLibTest() {
    private companion object {
        private const val USE_SITE_LIBRARY_NAME = "useSiteLibrary"
    }

    private lateinit var useSiteMockLibraryFacility: MockLibraryFacility

    override fun setUp() {
        super.setUp()

        val sourcesDir = testDataDirectory.resolve(testDirectoryPath).resolve("src")

        // Remove '<caret>' from sources
        val patchedSourcesDir = FileUtil.createTempDirectory("src", "")
        FileUtil.copyDir(sourcesDir, patchedSourcesDir)
        patchedSourcesDir.walk().filter { it.isFile && it.extension in KOTLIN_FILE_EXTENSIONS }.forEach(::patchSource)

        val compilationClasspath = listOf(mockLibraryFacility.target)
        val mockLibraryFacility = MockLibraryFacility(
            patchedSourcesDir,
            classpath = compilationClasspath,
            libraryName = USE_SITE_LIBRARY_NAME
        )

        useSiteMockLibraryFacility = mockLibraryFacility.apply { setUp(module) }
    }

    // Remove <caret> from sources
    private fun patchSource(file: File) {
        val text = file.readText()
        file.writeText(text.replace("<caret>", ""))
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { useSiteMockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun configureTest() {
        super.configureTest()

        // Read carets from the file opened as a source
        val caretsAndSelections = myFixture.editor.caretModel.caretsAndSelections

        // Then open the same file, but inside an attached library to test cross-library navigation
        val useSiteLibrary = LibraryUtil.findLibrary(module, USE_SITE_LIBRARY_NAME)!!
        val virtualFile = useSiteLibrary.getFiles(OrderRootType.SOURCES).firstNotNullOf { it.findChild(dataFile().name) }
        myFixture.openFileInEditor(virtualFile)

        // Apply the carets we got above
        myFixture.editor.caretModel.caretsAndSelections = caretsAndSelections
    }
}