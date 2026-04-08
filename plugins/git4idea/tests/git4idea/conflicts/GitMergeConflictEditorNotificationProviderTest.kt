// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.conflicts

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveWithAgentContext
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import git4idea.test.GitScenarios.conflict
import git4idea.test.GitSingleRepoTest
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

internal class GitMergeConflictEditorNotificationProviderTest : GitSingleRepoTest() {
  fun `test unresolved conflict banner appends contributed links in provider order`() {
    val firstAction = TestResolveAction("First")
    val secondAction = TestResolveAction("Second")

    withRegisteredResolveProviders(
      TestProvider(order = 10, action = secondAction),
      TestProvider(order = -1, action = firstAction),
    ) {
      val conflictFile = createConflictFile()
      val panel = createConflictPanel(conflictFile)

      val links = UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java)
      assertEquals(
        listOf(
          GitBundle.message("link.label.merge.conflicts.suggest.resolve.show.window"),
          "First",
          "Second",
        ),
        links.map(HyperlinkLabel::getText),
      )

      runInEdtAndWait {
        links[1].doClick()
      }

      assertEquals(1, firstAction.performedCount)
      val context = firstAction.performedContext
      assertNotNull(context)
      assertSame(project, context!!.project)
      assertEquals(listOf(conflictFile), context.files)
      assertTrue(context.isLaunchContextValid())
    }
  }

  fun `test unresolved conflict banner omits resolve with agent link when action is disabled`() {
    withRegisteredResolveProviders(TestProvider(action = TestResolveAction("Resolve with Agent", enabled = false))) {
      val panel = createConflictPanel(createConflictFile())

      val links = UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java)
      assertEquals(listOf(GitBundle.message("link.label.merge.conflicts.suggest.resolve.show.window")), links.map(HyperlinkLabel::getText))
    }
  }

  private fun createConflictFile(): VirtualFile {
    conflict(repo, "feature")
    git("checkout feature")
    git("rebase master", true)
    updateChangeListManager()
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file("conflict.txt").file)!!
  }

  private fun createConflictPanel(file: VirtualFile): EditorNotificationPanel {
    val provider = MergeConflictResolveUtil.NotificationProvider()
    val panelFactory = runReadActionBlocking {
      provider.collectNotificationData(project, file)
    }
    assertNotNull(panelFactory)
    var panel: EditorNotificationPanel? = null
    runInEdtAndWait {
      panel = panelFactory!!.apply(TestFileEditor(file)) as EditorNotificationPanel
    }
    return panel!!
  }

  private fun withRegisteredResolveProviders(vararg providers: MergeResolveActionProvider, block: () -> Unit) {
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, providers.toList(), testRootDisposable)
    block()
  }

  private class TestProvider(
    override val action: com.intellij.openapi.project.DumbAwareAction,
    override val order: Int = 0,
  ) : MergeResolveActionProvider

  private class TestResolveAction(
    text: String,
    private val enabled: Boolean = true,
  ) : com.intellij.openapi.project.DumbAwareAction(text) {
    var performedContext: MergeResolveWithAgentContext? = null
      private set
    var performedCount: Int = 0
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = e.getData(MergeResolveWithAgentContext.KEY) != null
      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      performedCount++
      performedContext = e.getData(MergeResolveWithAgentContext.KEY)
    }
  }

  private class TestFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val component = JPanel()

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = "GitMergeConflictEditorNotificationTest"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() = Unit
  }
}
