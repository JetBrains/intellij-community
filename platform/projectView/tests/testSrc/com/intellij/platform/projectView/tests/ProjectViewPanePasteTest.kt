// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.CopyPasteManagerEx
import com.intellij.ide.PsiCopyPasteManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Paste for the frontend Project View, end to end: copying a file node and pasting it into a directory node
 * runs the real refactoring in the backend pane model and the new file appears in the frontend tree.
 *
 * Kept apart from [ProjectViewPaneCopyPasteTest] because this one mutates the project it runs in, and the
 * project fixture is shared by every test of a class.
 */
@TestApplication
internal class ProjectViewPanePasteTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewPanePasteTest::class.java, BLUEPRINT) }

    // openAfterCreation, because pasting runs a real refactoring, and CopyFilesOrDirectoriesHandler ends up
    // in DumbService.completeJustSubmittedTasks, which asserts that the project is initialized.
    private val project = projectFixture(
      pathFixture = tempPathFixture(subdirName = "pvPaste"),
      openAfterCreation = true,
    )
    private val module = project.moduleFixture(name = "pvPaste")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
  }

  @Test
  fun `copy and paste duplicates the file into the target directory`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get()
    val project = project.get()
    try {
      withProjectViewPane(project) { pane ->
        val hello = pane.expand("pvPaste", "src", "Hello.txt")
        val sub = pane.expand("pvPaste", "src", "sub")

        pane.requestCopy(hello)
        waitUntil("Hello.txt should have been copied into the clipboard") {
          readAction {
            PsiCopyPasteManager.getInstance().getElements(BooleanArray(1))?.map { (it as PsiFileSystemItem).name }
          } == listOf("Hello.txt")
        }

        pane.requestPaste(sub)

        // In unit test mode CopyFilesOrDirectoriesHandler skips its dialog and copies into the target
        // directory under the same name, so the tree gains src/sub/Hello.txt.
        waitUntil("Hello.txt should have been pasted into src/sub") {
          pane.dumpSubtree("pvPaste", "src", "sub").lines().map { it.trim() }.contains("Hello.txt")
        }
        assertEquals(
          """
          src
           sub
            Hello.txt
            World.txt
           Hello.txt
          """.trimIndent(),
          pane.dumpSubtree("pvPaste", "src"),
        )
      }
    }
    finally {
      withContext(Dispatchers.EDT) {
        // In unit test mode CopyFilesOrDirectoriesHandler also opens the pasted file in an editor, and the
        // resulting EditorHistoryManager entry holds a VirtualFilePointer that outlives the project fixture.
        EditorHistoryManager.getInstance(project).removeAllFiles()
        // Likewise, the copied PSI keeps the project reachable and would trip the leak check at teardown,
        // both through PsiCopyPasteManager.myRecentData and through the clipboard history.
        PsiCopyPasteManager.getInstance().clear()
        val copyPasteManager = CopyPasteManagerEx.getInstanceEx()
        for (content in copyPasteManager.allContents) {
          copyPasteManager.removeContent(content)
        }
      }
    }
  }
}
