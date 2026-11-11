// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(30)
internal class SettingsTreeViewSelectionTest {
  private val project by projectFixture()
  private val pages: List<Configurable> = listOf(Page("A"), Page("B"), Page("C"), Page("D"))

  @Test
  fun `a key move in the tree shows the page after the wait`(): Unit = withEditor { editor, selections ->
    val tree = editor.treeView.tree
    val start = System.nanoTime()
    pressKey(tree, KeyEvent.VK_DOWN) { tree.processKeyEvent(it) }

    assertThat(editor.treeView.selectedConfigurable).isSameAs(pages[1])
    assertThat(selections).containsExactly(pages[0])

    waitFor("the page of the key move") { selections.last() === pages[1] }
    assertThat((System.nanoTime() - start).nanoseconds.inWholeMilliseconds)
      .isGreaterThanOrEqualTo(SettingsTreeView.KEY_SELECTION_DELAY_MS.toLong())
  }

  @Test
  fun `a key that the search field forwards to the tree also waits`(): Unit = withEditor { editor, selections ->
    val search = UIUtil.findComponentOfType(editor, SettingsSearch::class.java)!!
    pressKey(search.textEditor, KeyEvent.VK_DOWN) { search.onTextKeyEvent(it) }

    assertThat(editor.treeView.selectedConfigurable).isSameAs(pages[1])
    assertThat(selections).containsExactly(pages[0])

    waitFor("the page of the forwarded key") { selections.last() === pages[1] }
  }

  @Test
  fun `only the last of several key moves shows its page`(): Unit = withEditor { editor, selections ->
    val tree = editor.treeView.tree
    pressKey(tree, KeyEvent.VK_DOWN) { tree.processKeyEvent(it) }
    pressKey(tree, KeyEvent.VK_DOWN) { tree.processKeyEvent(it) }

    waitFor("the page of the last key move") { selections.last() === pages[2] }
    assertThat(selections).containsExactly(pages[0], pages[2])
  }

  @Test
  fun `a selection outside a key event shows the page at once and drops the waiting key move`(): Unit = withEditor { editor, selections ->
    val tree = editor.treeView.tree
    pressKey(tree, KeyEvent.VK_DOWN) { tree.processKeyEvent(it) }
    tree.setSelectionRow(3)

    assertThat(selections).containsExactly(pages[0], pages[3])

    pressKey(tree, KeyEvent.VK_UP) { tree.processKeyEvent(it) }
    waitFor("the page of the next key move") { selections.last() === pages[2] }
    assertThat(selections).containsExactly(pages[0], pages[3], pages[2])
  }

  @Test
  fun `Enter in the search field during the wait opens the page of the selected row`(): Unit = withEditor { editor, selections ->
    val tree = editor.treeView.tree
    val search = UIUtil.findComponentOfType(editor, SettingsSearch::class.java)!!
    pressKey(tree, KeyEvent.VK_DOWN) { tree.processKeyEvent(it) }
    search.textEditor.postActionEvent()

    waitFor("the page of the selected row") { selections.last() === pages[1] }
    assertThat(editor.treeView.selectedConfigurable).isSameAs(pages[1])
    assertThat(selections).containsExactly(pages[0], pages[1])
  }

  @Test
  fun `a reload before the tree root loads selects the page in the new tree`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    withForcedRespectIsShowingClientProperty {
      val disposable = Disposer.newDisposable()
      try {
        val editor = createEditor(disposable)
        val treeView = editor.treeView
        // A configurable extension can register while the dialog still loads its tree.
        assertThat(treeView.tree.model.root).isNull()

        treeView.reloadWithSelection(pages[2])
        markAsShowing(editor)

        waitFor("the reloaded tree") { treeView.tree.rowCount == pages.size }
        waitFor("the page of the reload") { treeView.selectedConfigurable === pages[2] }
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  /**
   * Runs [block] on the EDT with an editor that shows the first page.
   * The block gets the list of the pages that the editor made current, in order.
   * The context is `Dispatchers.EDT`, because the tree takes the write-intent lock for a key event.
   */
  private fun withEditor(block: suspend (SettingsEditor, List<Configurable?>) -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      // The key queue of the tree runs only while the tree shows, and a headless test has no window.
      withForcedRespectIsShowingClientProperty {
        val disposable = Disposer.newDisposable()
        try {
          val editor = createEditor(disposable)
          val selections = ArrayList<Configurable?>()
          editor.addOptionsListener(object : OptionsEditorColleague.Adapter() {
            override fun onSelected(configurable: Configurable?, oldConfigurable: Configurable?): Promise<in Any> {
              selections.add(configurable)
              return resolvedPromise()
            }
          })
          markAsShowing(editor)

          waitFor("the first page") { selections.isNotEmpty() && editor.treeView.tree.rowCount == pages.size }
          assertThat(selections).containsExactly(pages[0])
          block(editor, selections)
        }
        finally {
          Disposer.dispose(disposable)
        }
      }
    }
  }

  private fun createEditor(disposable: Disposable): SettingsEditor {
    val group = object : ConfigurableGroup {
      override fun getDisplayName(): String = "Group"

      override fun getConfigurables(): Array<Configurable> = pages.toTypedArray()
    }
    return SettingsEditor(
      disposable, project, listOf(group), pages[0], null, true,
      ISettingsTreeViewFactory { f, g -> SettingsTreeView(f, g) },
      spotlightPainterFactory,
      null,
      false,
    )
  }

  private val spotlightPainterFactory = object : SpotlightPainterFactory {
    override fun createSpotlightPainter(
      project: Project, target: JComponent, parent: Disposable, updater: (SpotlightPainter) -> Unit,
    ): SpotlightPainter = SpotlightPainter(target, updater)
  }

  private class Page(private val name: String) : Configurable {
    override fun getDisplayName(): String = name

    override fun createComponent(): JComponent = JPanel()

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    override fun toString(): String = name
  }
}

/** Puts [component] into a container that counts as showing, as a window would. */
private fun markAsShowing(component: JComponent) {
  val container = JPanel()
  ComponentUtil.forceMarkAsShowing(container, true)
  withShowingChanged { container.add(component) }
}

/**
 * Dispatches a key press through the IDE event queue, so that the key event is the current event while [handle] runs.
 * A headless test has no focus owner, so [handle] gives the event to the component that a real key press reaches.
 */
private fun pressKey(source: Component, keyCode: Int, handle: (KeyEvent) -> Unit) {
  val event = KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED)
  val disposable = Disposer.newDisposable()
  try {
    IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.NonLockedEventDispatcher { dispatched ->
      if (dispatched !== event) return@NonLockedEventDispatcher false
      handle(event)
      true
    }, disposable)
    IdeEventQueue.getInstance().dispatchEvent(event)
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private suspend fun waitFor(what: String, condition: () -> Boolean) {
  withTimeoutOrNull(5.seconds) {
    while (!condition()) {
      delay(10.milliseconds)
    }
  }
  assertThat(condition()).describedAs(what).isTrue()
}
