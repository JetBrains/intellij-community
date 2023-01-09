// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.createFile
import com.intellij.util.io.write
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.test.KotlinExpectedHighlightingData
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File
import java.nio.file.Files

abstract class AbstractLineMarkersTestInLibrarySources : AbstractLineMarkersTest() {
    private var libraryCleanPath: String? = null
    private var libraryClean: File? = null

    private val libraryOriginal = IDEA_TEST_DATA_DIR.resolve("codeInsightInLibrary/_library")
    private var mockLibraryFacility: MockLibraryFacility? = null
    override fun setUp() {
        super.setUp()

        if (libraryCleanPath == null) {
            val libraryClean = Files.createTempDirectory("lineMarkers_library")
            libraryCleanPath = libraryClean.toString()

            for (file in libraryOriginal.walkTopDown().filter { !it.isDirectory }) {
                val text = file.readText().replace("</?lineMarker.*?>".toRegex(), "")
                val cleanFile = libraryClean.resolve(file.relativeTo(libraryOriginal).path)
                cleanFile.createFile()
                cleanFile.write(text)
            }
            this.libraryClean = File(libraryCleanPath)
        }

        mockLibraryFacility = MockLibraryFacility(source = libraryClean!!)
        mockLibraryFacility?.setUp(module)
    }

    fun doTestWithLibrary(path: String) {
        doTest(path) {
            val fileSystem = VirtualFileManager.getInstance().getFileSystem("file")
            val project = myFixture.project
            for (file in libraryOriginal.walkTopDown().filter { !it.isDirectory }) {
                myFixture.openFileInEditor(fileSystem.findFileByPath(file.absolutePath)!!)
                val data = KotlinExpectedHighlightingData(myFixture.editor.document)
                data.init()

                val librarySourceFile = libraryClean!!.resolve(file.relativeTo(libraryOriginal).path)
                myFixture.openFileInEditor(fileSystem.findFileByPath(librarySourceFile.absolutePath)!!)
                val document = myFixture.editor.document
                PsiDocumentManager.getInstance(project).commitAllDocuments()

                if (!RootKindFilter.librarySources.matches(myFixture.file)) {
                    throw AssertionError("File ${myFixture.file.virtualFile.path} should be in library sources!")
                }

                doAndCheckHighlighting(myFixture.file, document, data, file)
            }
        }
    }

    override fun tearDown() = runAll(
        ThrowableRunnable { mockLibraryFacility?.tearDown(module) },
        ThrowableRunnable { libraryClean?.deleteRecursively() },
        ThrowableRunnable { super.tearDown() }
    )
}
