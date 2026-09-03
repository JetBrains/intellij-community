// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.conflicts

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import git4idea.test.GitScenarios.conflict
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
internal class GitMergeConflictEditorNotificationProviderTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `test unresolved conflict banner appends contributed links in provider order`(): Unit = with(context) {
    val firstAction = TestResolveAction("First")
    val secondAction = TestResolveAction("Second")

    withRegisteredResolveProviders(
      TestProvider(order = 10, action = secondAction),
      TestProvider(order = -1, action = firstAction),
    ) {
      val conflictFile = createConflictFile()
      val panel = createConflictPanel(conflictFile)

      val links = UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java)
      assertThat(links.map(HyperlinkLabel::getText)).containsExactly(
        GitBundle.message("link.label.merge.conflicts.suggest.resolve.show.window"),
        "First",
        "Second",
      )

      timeoutRunBlocking(context = Dispatchers.EDT) {
        links[1].doClick()
      }

      assertThat(firstAction.performedCount).isEqualTo(1)
      val context = firstAction.performedContext
      assertThat(context).isNotNull()
      assertThat(context!!.project).isSameAs(project)
      assertThat(context.selectionHintFiles).containsExactly(conflictFile)
      assertThat(context.isContextValid()).isTrue()
    }
  }

  @Test
  fun `test unresolved conflict banner omits resolve with agent link when action is disabled`(): Unit = with(context) {
    withRegisteredResolveProviders(TestProvider(action = TestResolveAction("Resolve with Agent", enabled = false))) {
      val panel = createConflictPanel(createConflictFile())

      val links = UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java)
      assertThat(links.map(HyperlinkLabel::getText))
        .containsExactly(GitBundle.message("link.label.merge.conflicts.suggest.resolve.show.window"))
    }
  }

  private fun GitSingleRepoContext.createConflictFile(): VirtualFile {
    conflict(repo, "feature")
    git("checkout feature")
    git("rebase master", true)
    updateChangeListManager()
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file("conflict.txt").file)!!
  }

  private fun GitSingleRepoContext.createConflictPanel(file: VirtualFile): EditorNotificationPanel {
    val provider = MergeConflictResolveUtil.NotificationProvider()
    val panelFactory = runReadActionBlocking {
      provider.collectNotificationData(project, file)
    }
    assertThat(panelFactory).isNotNull()
    return timeoutRunBlocking(context = Dispatchers.EDT) {
      panelFactory!!.apply(TestFileEditor(file)) as EditorNotificationPanel
    }
  }

  private fun withRegisteredResolveProviders(vararg providers: MergeResolveActionProvider, block: () -> Unit) {
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, providers.toList(), disposable)
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
    var performedContext: MergeResolveActionContext? = null
      private set
    var performedCount: Int = 0
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = e.getData(MergeResolveActionContext.KEY) != null
      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      performedCount++
      performedContext = e.getData(MergeResolveActionContext.KEY)
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
