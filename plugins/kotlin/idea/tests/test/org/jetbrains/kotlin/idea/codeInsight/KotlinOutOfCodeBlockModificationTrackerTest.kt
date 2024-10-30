// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiManagerEx
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import kotlin.test.assertNotEquals

class KotlinOutOfCodeBlockModificationTrackerTest : KotlinLightCodeInsightFixtureTestCase() {
    /**
     * Provided document guarantees commitDocument with events based on the change
     */
    fun `test mod counter doesn't change on editing comments - document provided - psi provided`() {
        val foo = myFixture.configureByText("Foo.kt", initialText())

        doTestChangeComments(foo.virtualFile, changeExpected = { false })
    }

    /**
     * No document means that there would be no real commitDocument with all required events fired on external change
     * No psi means that [com.intellij.psi.PsiTreeChangeEvent.PROP_UNLOADED_PSI] should be thrown instead of normal events as no psi was build yet
     */
    fun `test mod counter changes on editing comments - no document - no psi`() {
        val foo = myFixture.tempDirFixture.createFile("Foo.kt", initialText())

        doTestChangeComments(foo, changeExpected = { true })
    }

    /**
     * No document means that there would be no real commitDocument with all required events fired on external change
     * and given the psi was created, only general childrenChange events would be possible
     */
    fun `test mod counter changes on editing comments - no document - psi provided`() {
        val foo = myFixture.addFileToProject("Foo.kt", initialText())

        assertNull(FileDocumentManager.getInstance().getCachedDocument(foo.virtualFile))
        doTestChangeComments(
            foo.virtualFile,
            changeExpected = { true })
    }

    /**
     * Provided document leads to `documentChanged` but because no psi is provided
     * [com.intellij.psi.impl.PsiDocumentManagerBase.documentChanged] fires
     * [com.intellij.psi.impl.file.impl.FileManagerImpl.firePropertyChangedForUnloadedPsi]
     * which inc the trackers
     */
    fun `test mod counter changes on editing comments - document provided - no psi`() {
        val foo = myFixture.tempDirFixture.createFile("Foo.kt", initialText())

        FileDocumentManager.getInstance().getDocument(foo) //ensure document is loaded for some reason

        val viewProvider = PsiManagerEx.getInstanceEx(project).fileManager.findCachedViewProvider(foo)
        assertNull("ViewProvider: $viewProvider", viewProvider)

        doTestChangeComments(
            foo,
            changeExpected = { PsiManagerEx.getInstanceEx(project).fileManager.findCachedViewProvider(foo) == null })
    }

    fun `test file rename fires OOBM`() {
        val foo = myFixture.tempDirFixture.createFile("Foo.kt", initialText())

        // to avoid incrementing `KotlinCodeBlockModificationListener` via `propUnloadedPsi` which is thrown when no PSI is available
        myFixture.openFileInEditor(foo)

        val modificationTracker = KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
        val initialModificationCount = modificationTracker.modificationCount

        runWriteAction {
            foo.rename(this, "Bar.kt")
        }

        assertNotEquals(initialModificationCount, modificationTracker.modificationCount)
    }

    private fun doTestChangeComments(foo: VirtualFile, changeExpected: () -> Boolean) {
        val modificationTracker = KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
        val initialModificationCount = modificationTracker.modificationCount
        runWriteAction {
            foo.setBinaryContent(
                """
                class Foo { 
                  //some changes in the comment
                }
            """.trimIndent().toByteArray()
            )
        }
        if (changeExpected()) {
            assertNotEquals(initialModificationCount, modificationTracker.modificationCount)
        } else {
            assertEquals(initialModificationCount, modificationTracker.modificationCount)
        }
    }

    private fun initialText() = """
                class Foo { 
                   //comments
                }
            """.trimIndent()
}