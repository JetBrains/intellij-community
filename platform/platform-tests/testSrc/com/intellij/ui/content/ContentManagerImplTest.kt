// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.content.impl.ContentManagerImpl
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

/**
 * Contract tests for [ContentManagerImpl].
 * A [TabbedPaneContentUI] is used because it is the default, simplest UI and has a deterministic selection policy.
 */
@TestApplication
@Suppress("DEPRECATION")  // the deprecated Disposer.isDisposed is the simplest fit for these assertions
internal class ContentManagerImplTest {
  private val project: Project by projectFixture()

  // ----------------------------------------------------------------------------------------------------------------
  // addContent
  // ----------------------------------------------------------------------------------------------------------------

  @Test
  fun `addContent registers the content and selects the first one`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      assertThat(manager.contentCount).isEqualTo(0)
      assertThat(manager.selectedContent).isNull()

      val c1 = content("c1")
      manager.addContent(c1)

      assertThat(manager.contentCount).isEqualTo(1)
      assertThat(manager.contents.toList()).containsExactly(c1)
      assertThat(c1.manager).isSameAs(manager)
      assertThat(manager.getIndexOfContent(c1)).isEqualTo(0)
      assertThat(c1.isValid).isTrue()
      // TabbedPaneContentUI does not allow an empty selection, so the first added content becomes selected.
      assertThat(manager.selectedContent).isSameAs(c1)
    }
  }

  @Test
  fun `adding a second content does not change the selection`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      val c2 = content("c2")
      manager.addContent(c1)
      manager.addContent(c2)

      assertThat(manager.contents.toList()).containsExactly(c1, c2)
      assertThat(manager.getIndexOfContent(c2)).isEqualTo(1)
      // Only the first content is auto-selected; adding more keeps the current selection.
      assertThat(manager.selectedContent).isSameAs(c1)
    }
  }

  @Test
  fun `addContent inserts at the requested index`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      val c2 = content("c2")
      val c3 = content("c3")
      manager.addContent(c1)
      manager.addContent(c2)
      manager.addContent(c3, 1)

      assertThat(manager.contents.toList()).containsExactly(c1, c3, c2)
      assertThat(manager.getIndexOfContent(c3)).isEqualTo(1)
    }
  }

  @Test
  fun `re-adding an already added content only reorders it`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      val c2 = content("c2")
      val c3 = content("c3")
      manager.addContent(c1)
      manager.addContent(c2)
      manager.addContent(c3)

      val recorder = Recorder().also { manager.addContentManagerListener(it) }
      manager.addContent(c1, 2)

      assertThat(manager.contents.toList()).containsExactly(c2, c3, c1)
      assertThat(manager.contentCount).isEqualTo(3)
      assertThat(c1.manager).isSameAs(manager)
      // Re-adding an already present content is a pure reorder: no contentAdded event is fired.
      assertThat(recorder.events).isEmpty()
    }
  }

  // ----------------------------------------------------------------------------------------------------------------
  // removeContent
  // ----------------------------------------------------------------------------------------------------------------

  @Test
  fun `removeContent with dispose=true removes and disposes the content`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      manager.addContent(c1)

      val removed = manager.removeContent(c1, /* dispose = */ true)

      assertThat(removed).isTrue()
      assertThat(manager.contentCount).isEqualTo(0)
      assertThat(c1.manager).isNull()
      assertThat(c1.isValid).isFalse()
      assertThat(manager.selectedContent).isNull()
      assertThat(Disposer.isDisposed(c1)).isTrue()
    }
  }

  @Test
  fun `removeContent with dispose=false removes but keeps the content alive`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      manager.addContent(c1)

      val removed = manager.removeContent(c1, /* dispose = */ false)

      assertThat(removed).isTrue()
      assertThat(manager.contentCount).isEqualTo(0)
      assertThat(c1.manager).isNull()
      assertThat(Disposer.isDisposed(c1)).isFalse()
    }
  }

  @Test
  fun `removeContent returns false for a content that is not in the manager`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val foreign = content("foreign")
      val recorder = Recorder().also { manager.addContentManagerListener(it) }

      val removed = manager.removeContent(foreign, /* dispose = */ true)

      assertThat(removed).isFalse()
      assertThat(Disposer.isDisposed(foreign)).isFalse()
      assertThat(recorder.events).isEmpty()
    }
  }

  @Test
  fun `removing the selected content selects a sibling`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      val c2 = content("c2")
      manager.addContent(c1)
      manager.addContent(c2)
      assertThat(manager.selectedContent).isSameAs(c1)

      manager.removeContent(c1, /* dispose = */ true)

      assertThat(manager.contents.toList()).containsExactly(c2)
      assertThat(manager.selectedContent).isSameAs(c2)
    }
  }

  @Test
  fun `removing the last content clears the selection`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      manager.addContent(c1)

      manager.removeContent(c1, /* dispose = */ true)

      assertThat(manager.contentCount).isEqualTo(0)
      assertThat(manager.selectedContent).isNull()
    }
  }

  // ----------------------------------------------------------------------------------------------------------------
  // disposal ownership
  // ----------------------------------------------------------------------------------------------------------------

  @Test
  fun `disposing the manager disposes the contents it still holds`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      manager.addContent(c1)

      Disposer.dispose(manager)

      assertThat(manager.isDisposed).isTrue()
      assertThat(Disposer.isDisposed(c1)).isTrue()
    }
  }

  @Test
  fun `a content removed with dispose=false is not disposed together with the manager`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      manager.addContent(c1)
      manager.removeContent(c1, /* dispose = */ false)
      assertThat(Disposer.isDisposed(c1)).isFalse()

      Disposer.dispose(manager)

      // The content was detached from the manager, so the manager must not dispose it: its lifecycle now belongs
      // to whoever took it over (e.g. an editor tab it was moved to).
      assertThat(Disposer.isDisposed(c1)).isFalse()

      // And it can still be disposed explicitly by its new owner.
      Disposer.dispose(c1)
      assertThat(Disposer.isDisposed(c1)).isTrue()
    }
  }

  // ----------------------------------------------------------------------------------------------------------------
  // listeners
  // ----------------------------------------------------------------------------------------------------------------

  @Test
  fun `adding the first content notifies about the addition and the selection`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val recorder = Recorder().also { manager.addContentManagerListener(it) }

      val c1 = content("c1")
      manager.addContent(c1)

      // Adding the first content both notifies about the addition and selects it. The relative order of the two
      // notifications is not part of the contract (it depends on the content UI's own listener), so ignore order.
      assertThat(recorder.events).containsExactlyInAnyOrder(
        Event(EventType.ADDED, c1, 0),
        Event(EventType.SELECTION_CHANGED, c1, 0),
      )
    }
  }

  @Test
  fun `adding a non-first content fires only contentAdded`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      manager.addContent(content("c1"))

      val recorder = Recorder().also { manager.addContentManagerListener(it) }
      val c2 = content("c2")
      manager.addContent(c2)

      assertThat(recorder.events).containsExactly(Event(EventType.ADDED, c2, 1))
    }
  }

  @Test
  fun `removeContent fires contentRemoveQuery then contentRemoved`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      val c2 = content("c2")
      manager.addContent(c1)
      manager.addContent(c2)

      // Remove the non-selected content, so the events aren't mixed with reselection notifications.
      val recorder = Recorder().also { manager.addContentManagerListener(it) }
      manager.removeContent(c2, /* dispose = */ true)

      assertThat(recorder.events).containsExactly(
        Event(EventType.REMOVE_QUERY, c2, 1),
        Event(EventType.REMOVED, c2, 1),
      )
      assertThat(manager.selectedContent).isSameAs(c1)
    }
  }

  @Test
  fun `consuming contentRemoveQuery vetoes the removal`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val c1 = content("c1")
      manager.addContent(c1)

      val vetoing = object : Recorder() {
        override fun contentRemoveQuery(event: ContentManagerEvent) {
          super.contentRemoveQuery(event)
          event.consume()
        }
      }
      manager.addContentManagerListener(vetoing)

      val removed = manager.removeContent(c1, /* dispose = */ true)

      assertThat(removed).isFalse()
      assertThat(manager.contentCount).isEqualTo(1)
      assertThat(c1.manager).isSameAs(manager)
      assertThat(Disposer.isDisposed(c1)).isFalse()
      // Only the (consumed) query is delivered; no contentRemoved follows.
      assertThat(vetoing.events).containsExactly(Event(EventType.REMOVE_QUERY, c1, 0))
    }
  }

  @Test
  fun `a removed listener stops receiving events`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withContentManager { manager ->
      val recorder = Recorder()
      manager.addContentManagerListener(recorder)
      manager.removeContentManagerListener(recorder)

      manager.addContent(content("c1"))

      assertThat(recorder.events).isEmpty()
    }
  }

  private fun content(name: String): Content {
    return ContentFactory.getInstance().createContent(JPanel(), name, false)
  }

  /**
   * Creates a fresh [ContentManagerImpl] and runs [body].
   */
  private fun withContentManager(body: (manager: ContentManagerImpl) -> Unit) {
    body(ContentManagerImpl(TabbedPaneContentUI(), /* canCloseContents = */ true, project))
  }

  private enum class EventType { ADDED, REMOVED, REMOVE_QUERY, SELECTION_CHANGED }

  private data class Event(val type: EventType, val content: Content, val index: Int)

  private open class Recorder : ContentManagerListener {
    val events: MutableList<Event> = mutableListOf()

    override fun contentAdded(event: ContentManagerEvent) {
      events.add(Event(EventType.ADDED, event.content, event.index))
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      events.add(Event(EventType.REMOVED, event.content, event.index))
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      events.add(Event(EventType.REMOVE_QUERY, event.content, event.index))
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      events.add(Event(EventType.SELECTION_CHANGED, event.content, event.index))
    }
  }
}
