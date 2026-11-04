// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Tests how [ProblemsViewHighlightingFileRoot] applies [ProblemsViewContextVisibility] to the problems of a file
 * with two contexts.
 *
 * The editor, the panel and the problem callbacks need the EDT. The owner check of
 * [ProblemsViewContextVisibility.supportsSimultaneousRendering] needs a background read action.
 */
@TestApplication
internal class ProblemsViewHighlightingFileRootContextsTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true) {}
  private val sourceRoot = projectFixture.moduleFixture("ProblemsViewHighlightingFileRootContextsTest").sourceRootFixture()
  private val project by projectFixture
  private val file by sourceRoot.virtualFileFixture("Owned.txt", "text")
  private val otherFile by sourceRoot.virtualFileFixture("Other.txt", "")
  private val disposable by disposableFixture()

  private val first = TestCodeInsightContext("first")
  private val second = TestCodeInsightContext("second")

  @Test
  fun `a dirty context that no editor shows is hidden`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      withEditorAndRoot(editorContext = first) { _, root ->
        root.problemAppeared(problem(first))
        root.problemAppeared(problem(second))

        assertEquals(listOf(first), root.contexts(), "Expected the view to hide the dirty context that no editor shows")
        assertEquals(1, root.getProblemCount(), "Expected the count to exclude the hidden context")
      }
    }
  }

  @Test
  fun `an analyzed context that no editor shows is shown`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      markAnalyzed(second)
      withEditorAndRoot(editorContext = first) { _, root ->
        root.problemAppeared(problem(first))
        root.problemAppeared(problem(second))

        assertEquals(setOf(first, second), root.contexts().toSet())
        assertEquals(2, root.getProblemCount())
      }
    }
  }

  @Test
  fun `the context of the editor is shown when it is dirty`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      withEditorAndRoot(editorContext = first) { _, root ->
        root.problemAppeared(problem(first))

        assertEquals(listOf(first), root.contexts())
      }
    }
  }

  @Test
  fun `a daemon event refreshes the visible contexts`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      withEditorAndRoot(editorContext = first) { editor, root ->
        root.problemAppeared(problem(first))
        root.problemAppeared(problem(second))
        assertEquals(listOf(first), root.contexts(), "precondition: the view shows the context of the editor")
        assertEquals(1, root.getProblemCount(), "precondition: the count has one problem")

        WriteAction.run<Throwable> {
          EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(second))
        }
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonStarting(listOf(textEditor))
        root.dispatchAllEvents()

        assertEquals(listOf(second), root.contexts(), "Expected the new context of the editor, and the dirty old one hidden")
        assertEquals(1, root.getProblemCount(), "Expected the count to follow the visible contexts")
      }
    }
  }

  @Test
  fun `a provider without simultaneous rendering shows the context of the editor only`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(SingleRenderingProvider(file, listOf(first, second)))
      markAnalyzed(first)
      markAnalyzed(second)
      withEditorAndRoot(editorContext = first) { _, root ->
        root.problemAppeared(problem(first))
        root.problemAppeared(problem(second))

        assertEquals(listOf(first), root.contexts(), "Expected only the context of the editor, although both are analyzed")
        assertEquals(1, root.getProblemCount())
      }
    }
  }

  @Test
  fun `a provider without simultaneous rendering shows the preferred context when no editor context is known`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(SingleRenderingProvider(file, listOf(first, second), preferredContext = second))
      markAnalyzed(first)
      markAnalyzed(second)
      EditorTracker.getInstance(project).setActiveEditorsInTests(emptyList())
      withRoot { root ->
        root.problemAppeared(problem(first))
        root.problemAppeared(problem(second))

        assertEquals(listOf(second), root.contexts(), "Expected the preferred context of the file")
        assertEquals(1, root.getProblemCount())
      }
    }
  }

  @Test
  fun `a problem callback on a background thread does not fail`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(SingleRenderingProvider(file, listOf(first, second)))
      markAnalyzed(first)
      markAnalyzed(second)
    }
    withEditorAndRoot(editorContext = first) { _, root ->
      val problem = problem(first)
      withContext(Dispatchers.EDT) {
        root.problemAppeared(problem)
        root.problemAppeared(problem(second))
        assertEquals(listOf(first), root.contexts(), "precondition: the view shows the context of the editor")
      }

      // the watcher calls the root from the thread that disposes the old root
      withContext(Dispatchers.Default) {
        root.problemDisappeared(problem)
      }

      withContext(Dispatchers.EDT) {
        assertEquals(emptyList<CodeInsightContext>(), root.contexts(), "Expected the problem gone and the other context still hidden")
      }
    }
  }

  @Test
  fun `the provider that owns the file decides about simultaneous rendering`() = timeoutRunBlocking {
    assertTrue(supportsSimultaneousRendering(file), "Expected the platform provider to support simultaneous rendering")

    withContext(Dispatchers.EDT) {
      registerProvider(SingleRenderingProvider(file, listOf(first, second)))
    }

    assertFalse(supportsSimultaneousRendering(file), "Expected the owner of the file to decide")
    assertTrue(supportsSimultaneousRendering(otherFile), "Expected no effect on a file that the provider does not own")
  }

  @Test
  fun `a shown context that becomes stale leaves the tree`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      markAnalyzed(second)
      withEditorAndRoot(editorContext = first) { editor, root ->
        val panel = root.panel
        root.problemAppeared(problem(second))
        val before = treeText(panel)
        assertEquals(listOf(second), root.contexts(), "precondition: the analyzed context is shown")
        assertTrue(before.contains("Owned.txt"), "precondition: the tree shows the file node\n$before")

        DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap.markAllFilesDirty("test")
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonStarting(listOf(textEditor))
        root.dispatchAllEvents()

        assertEquals(emptyList<CodeInsightContext?>(), root.contexts(), "precondition: the query hides the stale context")
        val after = treeText(panel)
        root.structureChanged()
        assertFalse(treeText(panel).contains("Owned.txt"), "control: a refresh drops the file node")
        assertFalse(after.contains("Owned.txt"), "Expected the tree to drop the stale context. after:\n$after")
      }
    }
  }

  @Test
  fun `problems that arrive before the file contexts are hidden in the tree`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(SingleRenderingProvider(file, listOf(first, second)))
      markAnalyzed(first)
      markAnalyzed(second)
      val editor = createEditor(first)
      EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
      val panel = TestHighlightingPanel(project)
      try {
        panel.setCurrentFile(file, document)
        val root = panel.currentRoot as ProblemsViewHighlightingFileRoot
        // the watcher delivers the existing highlighters before the read action of the file contexts finishes
        root.problemAppeared(problem(second))
        // no EDT event runs before this check, so the file contexts are not loaded yet
        assertEquals(listOf(second), root.contexts(), "precondition: the tree gets the problem before the file contexts")
        root.dispatchAllEvents()
        assertEquals(emptyList<CodeInsightContext?>(), root.contexts(), "precondition: the query hides the other context")
        val after = treeText(panel)
        root.structureChanged()
        assertFalse(treeText(panel).contains("Owned.txt"), "control: a refresh drops the file node")
        assertFalse(after.contains("Owned.txt"), "Expected the tree to drop the other context. after:\n$after")
      }
      finally {
        Disposer.dispose(panel)
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  @Test
  fun `an update of a hidden problem that becomes visible reaches the tree`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      withEditorAndRoot(editorContext = first) { editor, root ->
        val hiddenProblem = problem(second)
        root.problemAppeared(problem(first))
        root.problemAppeared(hiddenProblem)
        assertEquals(listOf(first), root.contexts(), "precondition: the dirty context is hidden")
        treeText(root.panel) // the tree loads and expands the file node, as a user sees it

        // the watcher reports an update before the tracker refreshes the view
        markAnalyzed(second)
        root.problemUpdated(hiddenProblem)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonFinished(listOf(textEditor))
        root.dispatchAllEvents()

        val after = treeText(root.panel)
        root.structureChanged()
        val refreshed = treeText(root.panel)
        assertEquals(refreshed, after, "Expected the tree to show the context that became visible")
      }
    }
  }

  @Test
  fun `a file node that a visibility change brings back is expanded`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      withEditorAndRoot(editorContext = first) { editor, root ->
        val hiddenProblem = problem(second)
        root.problemAppeared(hiddenProblem)
        root.dispatchAllEvents()
        assertEquals(emptyList<CodeInsightContext?>(), root.contexts(), "precondition: the dirty context is hidden")

        markAnalyzed(second)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonFinished(listOf(textEditor))
        root.dispatchAllEvents()
        val viaVisibility = loadedTreeText(root)

        // control: the same problem through problemAppeared on a visible context
        root.problemDisappeared(hiddenProblem)
        root.dispatchAllEvents()
        root.problemAppeared(problem(second))
        root.dispatchAllEvents()
        val viaAppeared = loadedTreeText(root)

        assertEquals(viaAppeared, viaVisibility, "Expected the file node expanded as after problemAppeared")
      }
    }
  }

  @Test
  fun `a new root hides a dirty context that no editor shows before the first visibility update`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      val editor = createEditor(first)
      EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
      val panel = TestHighlightingPanel(project)
      try {
        panel.setCurrentFile(file, document)
        val root = panel.currentRoot as ProblemsViewHighlightingFileRoot
        root.problemAppeared(problem(first))
        root.problemAppeared(problem(second))
        // a file switch creates a new root, and the watcher delivers the highlighters at once
        assertEquals(listOf<CodeInsightContext?>(first), root.contexts(), "Expected the tree to show the context of the editor only")
        assertEquals(1, root.getProblemCount(), "Expected the tab count to exclude the dirty context that no editor shows")
      }
      finally {
        Disposer.dispose(panel)
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  @Test
  fun `a repeated appearance of a hidden problem that became visible reaches the tree`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      withEditorAndRoot(editorContext = first) { editor, root ->
        val hiddenProblem = problem(second)
        root.problemAppeared(problem(first))
        root.problemAppeared(hiddenProblem)
        assertEquals(listOf(first), root.contexts(), "precondition: the dirty context is hidden")
        treeText(root.panel)

        // the watcher reports the same highlighter again before the tracker refreshes the view
        markAnalyzed(second)
        root.problemAppeared(hiddenProblem)
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonFinished(listOf(textEditor))
        root.dispatchAllEvents()

        val after = treeText(root.panel)
        root.structureChanged()
        val refreshed = treeText(root.panel)
        assertEquals(refreshed, after, "Expected the tree to show the context that became visible")
      }
    }
  }

  @Test
  fun `a visibility change that empties the file refreshes the tab count`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(TwoContextsProvider(file, listOf(first, second)))
      markAnalyzed(second)
      withEditorAndRoot(editorContext = first) { editor, root ->
        val panel = root.panel as TestHighlightingPanel
        root.problemAppeared(problem(second))
        root.dispatchAllEvents()
        PlatformTestUtil.waitWhileBusy(panel.tree)
        assertEquals(1, root.getProblemCount(), "precondition: the analyzed context is shown")
        val updatesBefore = panel.toolWindowUpdates

        DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap.markAllFilesDirty("test")
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonStarting(listOf(textEditor))
        root.dispatchAllEvents()

        assertEquals(0, root.getProblemCount(), "precondition: the stale context is hidden")
        assertTrue(panel.toolWindowUpdates > updatesBefore, "Expected a refresh of the tab count")
      }
    }
  }

  @Test
  fun `without a selected editor the order of the highlighted editors does not decide the only context`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      registerProvider(SingleRenderingProvider(file, listOf(first, second), preferredContext = second))
      markAnalyzed(first)
      markAnalyzed(second)
      val editors = listOf(createEditor(first), createEditor(second))
      try {
        val shown = listOf(editors, editors.reversed()).map { activeEditors ->
          EditorTracker.getInstance(project).setActiveEditorsInTests(activeEditors)
          var contexts = emptyList<CodeInsightContext?>()
          withRoot { root ->
            root.problemAppeared(problem(first))
            root.problemAppeared(problem(second))
            contexts = root.contexts()
          }
          contexts
        }
        assertEquals(listOf(listOf(second), listOf(second)), shown, "Expected the preferred context for both orders of the highlighted editors")
      }
      finally {
        editors.forEach { EditorFactory.getInstance().releaseEditor(it) }
      }
    }
  }

  /** The tree with every node expanded. */
  private fun treeText(panel: ProblemsViewPanel): String {
    PlatformTestUtil.waitWhileBusy(panel.tree)
    PlatformTestUtil.expandAll(panel.tree)
    return PlatformTestUtil.print(panel.tree, false)
  }

  /** The tree as the user sees it, with the expansion state that [root] set. */
  private suspend fun loadedTreeText(root: ProblemsViewHighlightingFileRoot): String {
    PlatformTestUtil.waitWhileBusy(root.panel.tree)
    root.dispatchAllEvents()
    return PlatformTestUtil.print(root.panel.tree, false)
  }

  private suspend fun supportsSimultaneousRendering(file: VirtualFile): Boolean =
    readAction { ProblemsViewContextVisibility.supportsSimultaneousRendering(project, file) }

  private fun registerProvider(provider: CodeInsightContextProvider) {
    WriteAction.run<Throwable> {
      CodeInsightContextManager.getInstance(project).registerTestOnlyCodeInsightContextProvider(provider, disposable)
    }
  }

  private val document: Document
    get() = FileDocumentManager.getInstance().getDocument(file)!!

  /** Creates an editor of the file that shows [context]. Call [EditorFactory.releaseEditor] after the test. */
  private fun createEditor(context: CodeInsightContext): Editor {
    val editor = EditorFactory.getInstance().createEditor(document, project)
    // the editor gets its default context asynchronously, and that would replace a context set before
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    WriteAction.run<Throwable> {
      EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(context))
    }
    return editor
  }

  /** Runs [block] with an editor of the file that shows [editorContext] and that the daemon highlights. */
  private suspend fun withEditorAndRoot(
    editorContext: CodeInsightContext,
    block: suspend (Editor, ProblemsViewHighlightingFileRoot) -> Unit,
  ) {
    val editor = withContext(Dispatchers.EDT) {
      val editor = createEditor(editorContext)
      EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
      editor
    }
    try {
      withRoot { root -> block(editor, root) }
    }
    finally {
      withContext(Dispatchers.EDT) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  /** Runs [block] with the root of a panel that shows the file. The panel lives and dies on the EDT. */
  private suspend fun withRoot(block: suspend (ProblemsViewHighlightingFileRoot) -> Unit) {
    val panel = withContext(Dispatchers.EDT) { TestHighlightingPanel(project) }
    try {
      val root = withContext(Dispatchers.EDT) {
        panel.setCurrentFile(file, document)
        (panel.currentRoot as ProblemsViewHighlightingFileRoot).also { it.dispatchAllEvents() }
      }
      block(root)
    }
    finally {
      withContext(Dispatchers.EDT) {
        Disposer.dispose(panel)
      }
    }
  }

  private fun ProblemsViewHighlightingFileRoot.contexts(): List<CodeInsightContext?> = getFileProblems(file).map { it.contextGroup }

  private fun markAnalyzed(context: CodeInsightContext) {
    DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap.markFileUpToDateForAllPasses(document, context)
  }

  private fun problem(context: CodeInsightContext): HighlightingProblem {
    val provider = object : ProblemsProvider {
      override val project: Project = this@ProblemsViewHighlightingFileRootContextsTest.project
    }
    return ContextProblem(provider, file, mock(RangeHighlighter::class.java), context)
  }

  /** Waits for the file contexts of the root and runs the EDT events. */
  private suspend fun ProblemsViewHighlightingFileRoot.dispatchAllEvents() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    awaitVisibilityUpdate()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }
}

/** This test module registers no Problems View actions. */
private class TestHighlightingPanel(project: Project) : HighlightingPanel(project, ProblemsViewState()) {
  var toolWindowUpdates = 0

  override fun updateToolWindowContent() {
    toolWindowUpdates++
    super.updateToolWindowContent()
  }

  override fun createToolbar(): ActionToolbar =
    ActionManager.getInstance().createActionToolbar(ActionPlaces.PROBLEMS_VIEW_TOOLBAR, DefaultActionGroup(), false)
}

/** A problem with a fixed context. The mocked highlighter gives it the identity. */
private class ContextProblem(
  provider: ProblemsProvider,
  file: VirtualFile,
  highlighter: RangeHighlighter,
  override val contextGroup: CodeInsightContext,
) : HighlightingProblem(provider, file, highlighter)

/** Owns [targetFile] with [contexts] and shows the results of all contexts together. */
private open class TwoContextsProvider(
  private val targetFile: VirtualFile,
  private val contexts: List<CodeInsightContext>,
  private val preferredContext: CodeInsightContext? = null,
) : CodeInsightContextProvider {
  override fun isOwnerOf(context: CodeInsightContext): Boolean = context is TestCodeInsightContext

  override fun getContexts(file: VirtualFile, project: Project): CodeInsightContextProvider.Answer =
    if (file == targetFile) CodeInsightContextProvider.Answer.Contexts(contexts) else CodeInsightContextProvider.Answer.NotOwned

  override fun getPreferredContext(file: VirtualFile, project: Project, contexts: List<CodeInsightContext>): CodeInsightContext? =
    preferredContext

  override fun subscribeToChanges(project: Project, invalidator: CodeInsightContextProvider.Invalidator) {}
}

/** A [TwoContextsProvider] whose results the UI shows one context at a time. */
private class SingleRenderingProvider(
  targetFile: VirtualFile,
  contexts: List<CodeInsightContext>,
  preferredContext: CodeInsightContext? = null,
) : TwoContextsProvider(targetFile, contexts, preferredContext) {
  override fun supportsSimultaneousRendering(): Boolean = false
}
