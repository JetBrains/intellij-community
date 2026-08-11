// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.CopyPasteManagerEx
import com.intellij.ide.PsiCopyPasteManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
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
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Tests cut and copy for the frontend Project View: the requests the frontend copy/paste provider sends carry
 * node IDs only, and everything that needs PSI happens in the backend pane model.
 *
 * The provider itself is not involved (it only maps a data context to those requests); the requests are sent
 * through the same channel it uses. See [ProjectViewPaneTest] for the pipeline this harness drives, and
 * [ProjectViewPanePasteTest] for the paste half.
 */
@TestApplication
internal class ProjectViewPaneCopyPasteTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
    private val blueprint: Path by lazy { projectViewTestDataPath(ProjectViewPaneCopyPasteTest::class.java, BLUEPRINT) }

    private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvCopyPaste"))
    private val module = project.moduleFixture(name = "pvCopyPaste")
    private val srcRoot = module.sourceRootFixture(
      isTestSource = false,
      pathFixture = tempPathFixture(subdirName = "src"),
      blueprintResourcePath = blueprint,
    )
  }

  @Test
  fun `copy puts the selected file into the clipboard`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get()
    withClipboardCleanUp {
      withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
        val hello = pane.expand("pvCopyPaste", "src", "Hello.txt")

        pane.requestCopy(hello)

        waitUntil("Hello.txt should have been copied into the clipboard") {
          clipboardFileNames() == listOf("Hello.txt") && isClipboardCopied()
        }
      }
    }
  }

  @Test
  fun `cut puts the selected file into the clipboard and marks it as cut`() = timeoutRunBlocking(60.seconds) {
    srcRoot.get()
    withClipboardCleanUp {
      withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
        val hello = pane.expand("pvCopyPaste", "src", "Hello.txt")

        pane.requestCut(hello)

        waitUntil("Hello.txt should have been cut into the clipboard") {
          clipboardFileNames() == listOf("Hello.txt") && !isClipboardCopied()
        }
        // What the Project View grays cut nodes by.
        waitUntil("The cut file should be reported as a cut element") {
          val element = clipboardElements().singleOrNull() ?: return@waitUntil false
          readAction { PsiCopyPasteManager.getInstance().isCutElement(element) }
        }
      }
    }
  }

  /**
   * The PSI put on the clipboard would otherwise outlive the project and trip the leak check at teardown.
   *
   * `PsiCopyPasteManager` drops its own transferables when the project closes, but the fixture disposes the
   * project without that, so both of its references have to go: `clear()` nulls `myRecentData`, and removing
   * the contents drops the earlier transferables that stay in the clipboard *history*
   * (`CopyPasteManagerWithHistory.myData`). Either one alone still holds the project via `MyData.project`.
   */
  private suspend fun withClipboardCleanUp(block: suspend () -> Unit) {
    try {
      block()
    }
    finally {
      withContext(Dispatchers.EDT) {
        PsiCopyPasteManager.getInstance().clear()
        val copyPasteManager = CopyPasteManagerEx.getInstanceEx()
        for (content in copyPasteManager.allContents) {
          copyPasteManager.removeContent(content)
        }
      }
    }
  }

  private suspend fun clipboardElements() = readAction {
    PsiCopyPasteManager.getInstance().getElements(BooleanArray(1))?.toList() ?: emptyList()
  }

  private suspend fun clipboardFileNames(): List<String> = readAction {
    PsiCopyPasteManager.getInstance().getElements(BooleanArray(1))?.map { (it as PsiFileSystemItem).name } ?: emptyList()
  }

  private suspend fun isClipboardCopied(): Boolean = readAction {
    val isCopied = BooleanArray(1)
    PsiCopyPasteManager.getInstance().getElements(isCopied)
    isCopied[0]
  }
}
