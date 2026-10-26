// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.BasePartiallyExcludedChangesTest
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.NoneChangesGroupingFactory
import com.intellij.openapi.vcs.changes.ui.StructuralRollbackDeleteProvider
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.RangeExclusionState
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.runAll
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicInteger

/**
 * The commit checkboxes of the hunk nodes with a real partial tracker. The commit includes the file, so a new tracker includes its ranges.
 * A range is shown as `base lines current lines state`.
 */
class StructuralHunkInclusionTest : BasePartiallyExcludedChangesTest() {
  override fun setUp() {
    super.setUp()
    Registry.get(StructuralChangesViewSettings.REGISTRY_KEY).setValue(true)
    PropertiesComponent.getInstance(project).setValue(OPTION_ID, true)
    setHolderPaths(FILE)
    include(FILE)
  }

  override fun tearDown() {
    runAll(
      { PropertiesComponent.getInstance(project).unsetValue(OPTION_ID) },
      // The registry listener of the Changes view needs the project, so the value returns before the project closes.
      { Registry.get(StructuralChangesViewSettings.REGISTRY_KEY).resetToDefault() },
      { super.tearDown() },
    )
  }

  fun `test a hunk checkbox changes the inclusion of its hunk`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      val a = hunk(file, 5)
      val b = hunk(file, 10)
      assertTrue(b.hasInclusion)

      b.setIncluded(false)
      assertEquals(listOf("5-6 5-6 included", "10-11 10-11 excluded"), ranges(file))
      assertEquals(ThreeStateCheckBox.State.SELECTED, a.getInclusionState(ThreeStateCheckBox.State.DONT_CARE))
      assertEquals(ThreeStateCheckBox.State.NOT_SELECTED, b.getInclusionState(ThreeStateCheckBox.State.DONT_CARE))

      b.setIncluded(true)
      assertEquals(listOf("5-6 5-6 included", "10-11 10-11 included"), ranges(file))
    }
  }

  fun `test a hunk checkbox after an edit changes no hunk`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      val b = hunk(file, 10)
      runCommand { file.document.insertString(0, PREFIX) }
      // Hunk A now has the current lines that hunk B had in the tree.
      assertEquals(listOf("0-0 0-5 included", "5-6 10-11 included", "10-11 15-16 included"), ranges(file))

      assertEquals(ThreeStateCheckBox.State.NOT_SELECTED, b.getInclusionState(ThreeStateCheckBox.State.NOT_SELECTED))
      b.setIncluded(false)
      assertEquals(listOf("0-0 0-5 included", "5-6 10-11 included", "10-11 15-16 included"), ranges(file))
    }
  }

  fun `test a hunk without a tracker changes its hunk after the tracker loads`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()
    val b = hunk(file, 10)

    b.setIncluded(false)
    waitForTracker()
    assertEquals(listOf("5-6 5-6 included", "10-11 10-11 excluded"), ranges(file))

    b.setIncluded(true)
    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test an edit before the tracker loads cancels the checkbox and releases the tracker`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()
    val b = hunk(file, 10)

    b.setIncluded(false)
    runCommand { file.document.insertString(0, PREFIX) }
    waitForTracker()

    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test a deleted hunk changes the inclusion of its deletion`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed"), text(LINES_WITH_GONE))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      hunk(file, 10).setIncluded(false)
      assertEquals(listOf("5-6 5-6 included", "10-11 10-10 excluded"), ranges(file))
    }
  }

  fun `test a hunk from an old base text changes no hunk`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed"), text(LINES_WITH_GONE))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      val deletion = hunk(file, 10)
      setBaseVersion(FILE, text(LINES, 10 to "line10-old"))
      refreshCLM()
      lstm.waitUntilBaseContentsLoaded()
      // The deletion is gone. A change of the line after it starts at the same current line.
      assertEquals(listOf("5-6 5-6 included", "10-11 10-11 included"), ranges(file))

      deletion.setIncluded(false)
      assertEquals(listOf("5-6 5-6 included", "10-11 10-11 included"), ranges(file))
    }
  }

  fun `test a hunk from an old base revision changes no hunk after the tracker loads`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed"), text(LINES_WITH_GONE))
    refreshCLM()
    val deletion = hunk(file, 10)
    setBaseVersion(FILE, text(LINES, 10 to "line10-old"))
    refreshCLM()

    deletion.setIncluded(false)
    waitForTracker()

    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test a hunk with other tracker boundaries changes the overlapping range`() {
    // The tree finds two hunks in these lines. A new tracker finds one range.
    val file = addLocalFile(FILE, " a\nb", "c\na \n a")
    refreshCLM()
    val hunks = hunks(file)
    assertEquals(2, hunks.size)

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      hunks.first().setIncluded(false)
      assertEquals(listOf("0-3 0-2 excluded"), ranges(file))
      assertEquals(ThreeStateCheckBox.State.NOT_SELECTED, hunks.last().getInclusionState(ThreeStateCheckBox.State.DONT_CARE))
    }
  }

  fun `test a rollback reverts its hunk`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      rollback(hunk(file, 10))
      assertEquals(text(LINES, 5 to "a-changed"), file.document.text)
      assertEquals(listOf("5-6 5-6 included"), ranges(file))
    }
  }

  fun `test undo in the tree brings back the hunks of a rollback in one step`() {
    val changed = text(LINES, 5 to "a-changed", 10 to "b-changed")
    val file = addLocalFile(FILE, changed, text(LINES))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      rollback(hunk(file, 5), hunk(file, 10))
      assertEquals(text(LINES), file.document.text)

      // The tree has no file editor, so its Undo takes the global stack.
      assertTrue(undoManager.isUndoAvailable(null))
      undoManager.undo(null)
      assertEquals(changed, file.document.text)
    }
  }

  fun `test a rollback after an edit reverts nothing`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      val b = hunk(file, 10)
      runCommand { file.document.insertString(0, PREFIX) }

      rollback(b)
      assertEquals(PREFIX + text(LINES, 5 to "a-changed", 10 to "b-changed"), file.document.text)
    }
  }

  fun `test a rollback without a tracker reverts its hunk after the tracker loads`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    rollback(hunk(file, 10))
    waitForTracker()
    assertEquals(text(LINES, 5 to "a-changed"), file.document.text)

    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test a rollback without a tracker waits for the modality of the call`() {
    val changed = text(LINES, 5 to "a-changed", 10 to "b-changed")
    val file = addLocalFile(FILE, changed, text(LINES))
    refreshCLM()

    rollback(hunk(file, 10))
    // A dialog opens before the tracker loads. The rollback started outside the dialog, so it changes the document after the dialog.
    val dialog = Any()
    LaterInvocator.enterModal(dialog)
    try {
      waitForTracker()
      assertEquals(changed, file.document.text)
    }
    finally {
      LaterInvocator.leaveModal(dialog)
    }
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(text(LINES, 5 to "a-changed"), file.document.text)

    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test without partial changelists a hunk has no checkbox and Delete reverts it`() {
    arePartialChangelistsSupported = false
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    val b = hunk(file, 10)
    assertFalse(b.hasInclusion)
    rollback(b)
    waitForTracker()
    assertEquals(text(LINES, 5 to "a-changed"), file.document.text)

    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test the Rollback action on a hunk reverts only the hunk`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed", 10 to "b-changed"), text(LINES))
    refreshCLM()

    val action = ActionManager.getInstance().getAction("ChangesView.Revert")
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, requireNotNull(StructuralRollbackDeleteProvider.create(listOf(hunk(file, 10)))))
      .build()
    val event = TestActionEvent.createTestEvent(action, context)
    action.update(event)
    assertTrue(event.presentation.isEnabledAndVisible)
    action.actionPerformed(event)
    waitForTracker()
    assertEquals(text(LINES, 5 to "a-changed"), file.document.text)

    releaseUnneededTrackers()
    file.assertNullTracker()
  }

  fun `test the updates start with the first tree build that shows hunks`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed"), text(LINES))
    refreshCLM()
    val refreshes = countTreeRefreshes()

    PropertiesComponent.getInstance(project).setValue(OPTION_ID, false)
    assertTrue(hunks(file).isEmpty())
    edit(file)
    assertEquals("A tree build with the structural tree off starts no listener", 0, refreshes.get())

    PropertiesComponent.getInstance(project).setValue(OPTION_ID, true)
    hunks(file)
    edit(file)
    assertTrue(refreshes.get() > 0)
  }

  fun `test an edit of a file without a change does not refresh the tree`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed"), text(LINES))
    val other = createLocalFile("other.txt", "other")
    refreshCLM()
    hunks(file)
    val refreshes = countTreeRefreshes()

    edit(other)
    assertEquals(0, refreshes.get())
  }

  fun `test the end of indexing refreshes the tree`() {
    val file = addLocalFile(FILE, text(LINES, 5 to "a-changed"), text(LINES))
    refreshCLM()
    hunks(file)
    val refreshes = countTreeRefreshes()

    DumbModeTestUtils.runInDumbModeSynchronously(project) { }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertTrue(refreshes.get() > 0)
  }

  /** Edits the file and commits its PSI. The PSI changes, so a listener of the PSI sees the edit. */
  private fun edit(file: VirtualFile) {
    assertNotNull(PsiManager.getInstance(project).findFile(file))
    runCommand { file.document.insertString(0, PREFIX) }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }

  /** Counts the requests to refresh the Changes tree. */
  private fun countTreeRefreshes(): AtomicInteger {
    val refreshes = AtomicInteger()
    project.messageBus.connect(testRootDisposable).subscribe(ChangesViewModifier.TOPIC, ChangesViewModifier.ChangesViewModifierListener {
      refreshes.incrementAndGet()
    })
    return refreshes
  }

  /** Reverts the hunks as Delete in the tree does. */
  private fun rollback(vararg hunks: ChangesBrowserStructuralNode<*>) {
    requireNotNull(hunks.first().getRollback()).rollback(hunks.toList())
  }

  private fun waitForTracker() {
    lstm.waitUntilBaseContentsLoaded()
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun hunks(file: VirtualFile): List<ChangesBrowserStructuralNode<*>> {
    val builder = TreeModelBuilder(project, NoneChangesGroupingFactory)
    val fileNode = ChangesBrowserChangeNode(project, requireNotNull(file.change), null)
    builder.myRoot.add(fileNode)
    val modifier = ChangesViewModifier.KEY.getExtensions(project).single { it.javaClass.simpleName == "StructuralChangesViewModifier" }
    modifier.modifyTreeModelBuilder(builder)
    return (0 until fileNode.childCount).map { fileNode.getChildAt(it) as ChangesBrowserStructuralNode<*> }
  }

  /** The hunk node that starts at the current line. */
  private fun hunk(file: VirtualFile, line: Int): ChangesBrowserStructuralNode<*> =
    hunks(file).single { (it.getNavigatable() as OpenFileDescriptor).line == line }

  private fun ranges(file: VirtualFile): List<String> = (file.tracker as PartialLocalLineStatusTracker).getRanges()!!.map {
    val state = when (it.exclusionState) {
      RangeExclusionState.Included -> "included"
      RangeExclusionState.Excluded -> "excluded"
      else -> "partial"
    }
    "${it.vcsLine1}-${it.vcsLine2} ${it.line1}-${it.line2} $state"
  }

  /** The lines with the given lines replaced. */
  private fun text(lines: List<String>, vararg replaced: Pair<Int, String>): String {
    val result = lines.toMutableList()
    for ((index, line) in replaced) result[index] = line
    return result.joinToString("\n")
  }

  private companion object {
    const val FILE = "file.txt"
    const val OPTION_ID = "ChangesView.ShowStructuralChanges"
    const val PREFIX = "x0\nx1\nx2\nx3\nx4\n"
    val LINES = (0..15).map { "line$it" }

    /** The lines with one more line before line 10. */
    val LINES_WITH_GONE = LINES.toMutableList().apply { add(10, "gone") }
  }
}
