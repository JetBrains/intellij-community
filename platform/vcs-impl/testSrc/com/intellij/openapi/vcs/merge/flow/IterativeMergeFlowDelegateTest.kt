// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.merge.MergeConflictIterativeDataHolder
import com.intellij.openapi.vcs.merge.MergeConflictsTreeTable
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JRootPane
import javax.swing.tree.DefaultMutableTreeNode

@TestApplication
@Suppress("DEPRECATION")
@RunInEdt(allMethods = false)
internal class IterativeMergeFlowDelegateTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()

  @Test
  @RunMethodInEdt
  fun resolveActionsRenderAsDialogButtonsAndUseMergeResolveContext(@TestDisposable disposable: Disposable) {
    val file = LightVirtualFile("conflicts/sample.txt", "text")
    val action = TestResolveAction("Resolve with Agent")
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, listOf(TestProvider(action)), disposable)
    var acceptAndFinishRequested = false
    val delegate = IterativeMergeFlowDelegate(
      project = project,
      iterativeDataHolder = MergeConflictIterativeDataHolder(project, disposable),
      table = createTable(),
      columnNames = listOf("Name", "Yours", "Theirs"),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      onAcceptAndFinish = { acceptAndFinishRequested = true },
      acceptForResolution = { _, _ -> },
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      resolveAutomatically = {},
      getGroupByDirectory = { false },
      updateTable = {},
    )

    val panel = delegate.createCenterPanel()
    delegate.createSouthPanel()
    delegate.onTreeChanged(listOf(file),
                           processedFiles = emptyList(),
                           unmergeableFileSelected = false,
                           unacceptableFileSelected = false)
    val button = UIUtil.findComponentsOfType(panel, JButton::class.java).single { it.text == "Resolve with Agent" }

    assertTrue(button.isVisible)
    assertTrue(button.isEnabled)
    assertNotNull(button.border)
    button.doClick()

    val context = action.performedContext
    assertNotNull(context)
    assertSame(project, context!!.project)
    assertEquals(listOf(file), context.selectionHintFiles)
    assertTrue(context.isContextValid())
    assertFalse(acceptAndFinishRequested)
    context.closeSourceUi()
    assertTrue(acceptAndFinishRequested)
  }

  @Test
  @RunMethodInEdt
  fun selectionDependentResolveActionAppearsAfterSelectionChanges(@TestDisposable disposable: Disposable) {
    val file = LightVirtualFile("conflicts/sample.txt", "text")
    val action = TestResolveAction("Resolve Selected", visibleWhenSelectionExists = true)
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, listOf(TestProvider(action)), disposable)
    val delegate = IterativeMergeFlowDelegate(
      project = project,
      iterativeDataHolder = MergeConflictIterativeDataHolder(project, disposable),
      table = createTable(),
      columnNames = listOf("Name", "Yours", "Theirs"),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      onAcceptAndFinish = {},
      acceptForResolution = { _, _ -> },
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      resolveAutomatically = {},
      getGroupByDirectory = { false },
      updateTable = {},
    )

    val panel = delegate.createCenterPanel()
    delegate.createSouthPanel()
    assertTrue(UIUtil.findComponentsOfType(panel, JButton::class.java).none { it.text == "Resolve Selected" && it.isVisible })

    delegate.onTreeChanged(listOf(file),
                           processedFiles = emptyList(),
                           unmergeableFileSelected = false,
                           unacceptableFileSelected = false)
    val button = UIUtil.findComponentsOfType(panel, JButton::class.java).single { it.text == "Resolve Selected" }
    assertTrue(button.isVisible)
    button.doClick()

    assertEquals(listOf(file), action.performedContext?.selectionHintFiles)
  }

  @Test
  @RunMethodInEdt
  fun magicWandButtonLogsPressedAndSessionEvents(@TestDisposable disposable: Disposable) {
    val file = LightVirtualFile("conflicts/sample.txt", "text")
    val delegate = IterativeMergeFlowDelegate(
      project = project,
      iterativeDataHolder = MergeConflictIterativeDataHolder(project, disposable),
      table = createTable(),
      columnNames = listOf("Name", "Yours", "Theirs"),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      onAcceptAndFinish = {},
      acceptForResolution = { _, _ -> },
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      resolveAutomatically = {},
      getGroupByDirectory = { false },
      updateTable = {},
    )

    val panel = delegate.createCenterPanel()
    delegate.createSouthPanel()
    delegate.onTreeChanged(listOf(file), processedFiles = emptyList(), unmergeableFileSelected = false, unacceptableFileSelected = false)

    val magicWand = UIUtil.findComponentsOfType(panel, JButton::class.java)
      .single { it.text == VcsBundle.message("multiple.file.iterative.merge.resolve.automatically") }
    assertTrue(magicWand.isEnabled)

    // No merge models are prepared in this harness, so the wand resolves nothing -> NO_EFFECT.
    // A single press logs the per-file event(s) plus one session event with the press's best result.
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      magicWand.doClick()
    }

    val pressed = events.single { it.event.id == "magic.wand.pressed" }
    assertEquals("NO_EFFECT", pressed.event.data["magic_wand_result"])
    assertEquals("ITERATIVE", pressed.event.data["flow"])
    val session = events.single { it.event.id == "magic.wand.session.result" }
    assertEquals("NO_EFFECT", session.event.data["magic_wand_result"])
    assertEquals("ITERATIVE", session.event.data["flow"])
  }

  @Test
  @RunMethodInEdt
  fun acceptAndFinishButtonLogsDialogAcceptEvent(@TestDisposable disposable: Disposable) {
    val file = LightVirtualFile("conflicts/sample.txt", "text")
    var acceptAndFinishRequested = false
    val delegate = IterativeMergeFlowDelegate(
      project = project,
      iterativeDataHolder = MergeConflictIterativeDataHolder(project, disposable),
      table = createTable(),
      columnNames = listOf("Name", "Yours", "Theirs"),
      mergeDialogCustomizer = MergeDialogCustomizer(),
      rootPane = JRootPane(),
      files = listOf(file),
      onClose = {},
      onAcceptAndFinish = { acceptAndFinishRequested = true },
      acceptForResolution = { _, _ -> },
      showMergeDialog = {},
      toggleGroupByDirectory = {},
      resolveAutomatically = {},
      getGroupByDirectory = { false },
      updateTable = {},
    )

    delegate.createCenterPanel()
    val south = delegate.createSouthPanel()
    // Marking the file as processed enables the accept button and puts the dialog in the "all reviewed" (two-button) state.
    delegate.onTreeChanged(listOf(file), processedFiles = listOf(file), unmergeableFileSelected = false, unacceptableFileSelected = false)

    val acceptButton = UIUtil.findComponentsOfType(south, JButton::class.java)
      .single { it.text == VcsBundle.message("multiple.file.iterative.merge.accept.finish") }
    assertTrue(acceptButton.isEnabled)

    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      acceptButton.doClick()
    }

    val accept = events.single { it.event.id == "dialog.accepted" }
    assertEquals(true, accept.event.data["all_reviewed"])
    assertEquals("ITERATIVE", accept.event.data["flow"])
    assertTrue(acceptAndFinishRequested)
  }

  private fun createTable(): MergeConflictsTreeTable {
    val columns = arrayOf(
      object : ColumnInfo<DefaultMutableTreeNode, Any>("Name") {
        override fun valueOf(node: DefaultMutableTreeNode): Any? = node.userObject
      },
      object : ColumnInfo<DefaultMutableTreeNode, Any>("Yours") {
        override fun valueOf(node: DefaultMutableTreeNode): Any? = null
      },
      object : ColumnInfo<DefaultMutableTreeNode, Any>("Theirs") {
        override fun valueOf(node: DefaultMutableTreeNode): Any? = null
      },
    )
    return MergeConflictsTreeTable(ListTreeTableModelOnColumns(DefaultMutableTreeNode(), columns))
  }

  private class TestProvider(
    override val action: DumbAwareAction,
  ) : MergeResolveActionProvider

  private class TestResolveAction(
    text: String,
    private val visibleWhenSelectionExists: Boolean = false,
  ) : DumbAwareAction(text) {
    var performedContext: MergeResolveActionContext? = null
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val context = e.getData(MergeResolveActionContext.KEY)
      e.presentation.isVisible = context?.isContextValid() == true &&
                                 (!visibleWhenSelectionExists || context.selectionHintFiles.isNotEmpty())
      e.presentation.isEnabled = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      performedContext = e.getData(MergeResolveActionContext.KEY)
    }
  }
}
