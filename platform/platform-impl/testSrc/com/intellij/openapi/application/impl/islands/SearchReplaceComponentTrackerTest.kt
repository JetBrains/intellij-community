// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Timeout
import java.awt.Component
import java.awt.Panel
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
@Timeout(30)
internal class SearchReplaceComponentTrackerTest {
  private val lifetime = Disposer.newDisposable()
  private val editorScope = CoroutineScope(SupervisorJob())

  private companion object {
    val project = projectFixture()
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      Disposer.dispose(lifetime)
    }
    editorScope.cancel()
  }

  private fun editor(owner: Disposable = lifetime): EditorComposite {
    val editor = EditorComposite(LightVirtualFile("search.txt"), emptyFlow(), project.get(), editorScope.childScope("Test editor"))
    Disposer.register(owner, editor)
    return editor
  }

  private fun editorPanel(owner: Disposable = lifetime): JComponent = editor(owner).component

  private fun containsSearch(panel: JComponent): Boolean = EditorSearchComponentState.get(panel)?.hasSearchComponents == true

  private fun trackedSearch(): Pair<JPanel, SearchReplaceComponentTracker> {
    val search = JPanel()
    return search to SearchReplaceComponentTracker(search)
  }

  @Test
  fun `lookup does not enumerate editor descendants`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val content = object : JPanel() {
        var enumerationAllowed = true

        override fun getComponents(): Array<Component> {
          check(enumerationAllowed) { "Editor descendants must not be enumerated" }
          return super.getComponents()
        }
      }
      repeat(10_000) { content.add(JPanel()) }
      editor.add(content)
      content.enumerationAllowed = false
      val unrelatedEditor = editorPanel()
      val (search, _) = trackedSearch()
      unrelatedEditor.add(search)

      assertThat(containsSearch(editor)).isFalse()
      content.add(search)
      assertThat(containsSearch(editor)).isTrue()
      assertThat(containsSearch(unrelatedEditor)).isFalse()
      content.enumerationAllowed = true
    }
  }

  @Test
  fun `lookup follows search panel attachment and removal`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val header = JPanel()
      val (search, _) = trackedSearch()

      assertThat(containsSearch(editor)).isFalse()
      header.add(search)
      editor.add(header)
      assertThat(containsSearch(editor)).isTrue()
      header.remove(search)
      assertThat(containsSearch(editor)).isFalse()
      header.add(search)
      assertThat(containsSearch(editor)).isTrue()
      editor.remove(header)
      assertThat(containsSearch(editor)).isFalse()
    }
  }

  @Test
  fun `lookup follows parent subtree moves between editors`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val firstEditor = editorPanel()
      val secondEditor = editorPanel()
      val header = JPanel()
      val (search, _) = trackedSearch()
      header.add(search)
      firstEditor.add(header)

      assertThat(containsSearch(firstEditor)).isTrue()
      assertThat(containsSearch(secondEditor)).isFalse()
      secondEditor.add(header)
      assertThat(containsSearch(firstEditor)).isFalse()
      assertThat(containsSearch(secondEditor)).isTrue()
    }
  }

  @Test
  fun `hidden search panel still belongs to the editor`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val (search, _) = trackedSearch()
      editor.add(search)
      search.isVisible = false

      assertThat(search.isShowing).isFalse()
      assertThat(containsSearch(editor)).isTrue()
    }
  }

  @Test
  fun `remaining search panel keeps the border suppressed`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val (firstSearch, _) = trackedSearch()
      val (secondSearch, _) = trackedSearch()
      editor.add(firstSearch)
      editor.add(secondSearch)

      editor.remove(firstSearch)
      assertThat(containsSearch(editor)).isTrue()
      editor.remove(secondSearch)
      assertThat(containsSearch(editor)).isFalse()
    }
  }

  @Test
  fun `lookup does not cross an AWT container`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val awtContainer = Panel()
      val (search, _) = trackedSearch()
      awtContainer.add(search)
      editor.add(awtContainer)

      assertThat(containsSearch(editor)).isFalse()
    }
  }

  @Test
  fun `editor disposal clears user data and keeps the listener`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val (search, tracker) = trackedSearch()
      editor.add(search)
      assertThat(search.hierarchyListeners).contains(tracker)

      Disposer.dispose(lifetime)

      assertThat(EditorSearchComponentState.get(editor)).isNull()
      assertThat(containsSearch(editor)).isFalse()
      assertThat(search.hierarchyListeners).contains(tracker)
    }
  }

  @Test
  fun `search that moves after its editor is disposed is tracked in the new editor`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val firstLifetime = Disposer.newDisposable(lifetime)
      val firstEditor = editorPanel(firstLifetime)
      val secondEditor = editorPanel()
      val (search, _) = trackedSearch()
      firstEditor.add(search)

      Disposer.dispose(firstLifetime)
      secondEditor.add(search)

      assertThat(containsSearch(secondEditor)).isTrue()
      secondEditor.remove(search)
      assertThat(containsSearch(secondEditor)).isFalse()
    }
  }

  @Test
  fun `disposing the former editor does not stop tracking in the new editor`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val firstLifetime = Disposer.newDisposable(lifetime)
      val firstEditor = editorPanel(firstLifetime)
      val secondEditor = editorPanel()
      val (search, tracker) = trackedSearch()
      firstEditor.add(search)
      secondEditor.add(search)

      Disposer.dispose(firstLifetime)

      assertThat(search.hierarchyListeners).contains(tracker)
      assertThat(containsSearch(secondEditor)).isTrue()
      secondEditor.remove(search)
      assertThat(containsSearch(secondEditor)).isFalse()
    }
  }

  @Test
  fun `nested editor states are both updated`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val outerEditor = editorPanel()
      val innerEditor = editorPanel()
      val (search, _) = trackedSearch()
      innerEditor.add(search)
      outerEditor.add(innerEditor)

      assertThat(containsSearch(outerEditor)).isTrue()
      assertThat(containsSearch(innerEditor)).isTrue()
      outerEditor.remove(innerEditor)
      assertThat(containsSearch(outerEditor)).isFalse()
      assertThat(containsSearch(innerEditor)).isTrue()
    }
  }

  @Test
  fun `disposing a nested editor preserves tracking for the outer editor`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val innerLifetime = Disposer.newDisposable(lifetime)
      val outerEditor = editorPanel()
      val innerEditor = editorPanel(innerLifetime)
      val (search, _) = trackedSearch()
      innerEditor.add(search)
      outerEditor.add(innerEditor)

      Disposer.dispose(innerLifetime)

      assertThat(EditorSearchComponentState.get(innerEditor)).isNull()
      assertThat(containsSearch(outerEditor)).isTrue()
    }
  }

  @Test
  fun `state is not installed for a disposed editor`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editorLifetime = Disposer.newDisposable(lifetime)
      val editor = editorPanel(editorLifetime)
      Disposer.dispose(editorLifetime)
      val (search, _) = trackedSearch()

      editor.add(search)

      assertThat(EditorSearchComponentState.get(editor)).isNull()
    }
  }

  @TestFactory
  fun `real search wrapper is tracked`(): List<DynamicTest> {
    return listOf("editor", "terminal", "large-file").map { kind ->
      dynamicTest(kind) {
        timeoutRunBlocking {
          withContext(Dispatchers.EDT) {
            val editorLifetime = Disposer.newDisposable(lifetime)
            val editor = editorPanel(editorLifetime)
            val customization = IslandsUICustomization()
            val header = EditorHeaderComponent()
            val search = when (kind) {
              "editor" -> customization.configureSearchReplaceComponent(header)
              "terminal" -> customization.configureTerminalSearchReplaceComponent(header)
              "large-file" -> customization.configureLfeSearchReplaceComponent(header)
              else -> error("Unexpected search kind: $kind")
            }
            val tracker = search.hierarchyListeners.filterIsInstance<SearchReplaceComponentTracker>().single()
            assertThat(EditorSearchComponentState.get(editor)).isNull()
            editor.add(search)
            assertThat(containsSearch(editor)).isTrue()

            Disposer.dispose(editorLifetime)
            assertThat(EditorSearchComponentState.get(editor)).isNull()
            assertThat(search.hierarchyListeners).contains(tracker)
          }
        }
      }
    }
  }

  @Test
  fun `detached search is released while the editor stays open`(): Unit = timeoutRunBlocking {
    val watcher = withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val (search, tracker) = trackedSearch()
      editor.add(search)
      editor.remove(search)
      assertThat(containsSearch(editor)).isFalse()
      GCWatcher.tracking(search, tracker)
    }
    watcher.ensureCollectedWithinTimeout(5_000)
  }

  @Test
  fun `disposed editor does not retain the search panel or its editor`(): Unit = timeoutRunBlocking {
    val watcher = withContext(Dispatchers.EDT) {
      val editor = editorPanel()
      val (search, _) = trackedSearch()
      editor.add(search)
      assertThat(containsSearch(editor)).isTrue()
      Disposer.dispose(lifetime)
      GCWatcher.tracking(editor, search)
    }
    watcher.ensureCollectedWithinTimeout(5_000)
  }
}
