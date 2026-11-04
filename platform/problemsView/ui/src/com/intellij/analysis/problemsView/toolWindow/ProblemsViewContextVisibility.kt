// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SplitModeApiUsage")

package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Decides which code insight contexts the Problems View shows for one document.
 *
 * The daemon highlights only the contexts of the editors that it highlights. So after an edit, the highlighters of a
 * context that no editor shows are stale. The view hides such a context until the daemon analyzes it again.
 * A [CodeInsightContextProvider] without simultaneous rendering limits the view to one context.
 */
@ApiStatus.Internal
data class ProblemsViewContextVisibility(
  /** The contexts of the editors of the document. The context of the selected editor comes first. */
  private val editorContexts: List<CodeInsightContext>,
  /** `true` when the provider of the file does not support simultaneous rendering. */
  private val oneContextOnly: Boolean,
  /** The one context that the view shows when [oneContextOnly]. `null` hides every context. */
  private val onlyContext: CodeInsightContext?,
) {
  /**
   * @param isAnalyzed `true` when the daemon finished the analysis of the context, see
   * [FileStatusMap.allDirtyScopesAreNullFor]. Called for a context that no editor shows.
   */
  fun isVisible(context: CodeInsightContext?, isAnalyzed: (CodeInsightContext) -> Boolean): Boolean = when {
    context == null || context == anyContext() -> true // the global group
    oneContextOnly && context != onlyContext -> false
    context in editorContexts -> true
    else -> isAnalyzed(context)
  }

  companion object {
    /**
     * Finds the contexts of the editors of [document].
     * Without simultaneous rendering, the view shows the context of the selected editor.
     * Without a selected editor of [document], it shows [preferredContext] when an editor shows it or no editor has a context.
     * Else it shows the first context of the highlighted editors.
     */
    @RequiresEdt
    fun compute(
      project: Project,
      document: Document,
      supportsSimultaneousRendering: Boolean,
      preferredContext: CodeInsightContext?,
    ): ProblemsViewContextVisibility {
      val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor?.takeIf { it.document == document }
      val highlightedEditors = (DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl)?.selectedEditors.orEmpty()
        .mapNotNull { (it as? TextEditor)?.editor?.takeIf { editor -> editor.document == document } }
      val contextManager = EditorContextManager.getInstance(project)
      val selectedContext = selectedEditor?.let { contextManager.getCachedEditorContexts(it)?.mainContext }
      // the main context of the selected editor comes first
      val editorContexts = (listOfNotNull(selectedEditor) + highlightedEditors)
        .flatMap { contextManager.getCachedEditorContexts(it)?.allContexts.orEmpty() }
        .distinct()
      // the order of the highlighted editors is arbitrary, so the preferred context goes before it
      val onlyContext = selectedContext
                        ?: preferredContext?.takeIf { editorContexts.isEmpty() || it in editorContexts }
                        ?: editorContexts.firstOrNull()
      return ProblemsViewContextVisibility(
        editorContexts = editorContexts,
        oneContextOnly = !supportsSimultaneousRendering,
        onlyContext = onlyContext,
      )
    }

    /**
     * The visibility of a new root before the first [compute]. Any thread.
     * It shows the contexts of every editor of [document], and another context only when the daemon analyzed it.
     */
    fun provisional(project: Project, document: Document): ProblemsViewContextVisibility {
      val contextManager = EditorContextManager.getInstance(project)
      val editorContexts = EditorFactory.getInstance().editors(document, project).toList()
        .flatMap { contextManager.getCachedEditorContexts(it)?.allContexts.orEmpty() }
        .distinct()
      return ProblemsViewContextVisibility(editorContexts, oneContextOnly = false, onlyContext = null)
    }

    /** `false` when the [CodeInsightContextProvider] that owns [file] does not support simultaneous rendering. */
    @RequiresReadLock
    @RequiresBackgroundThread
    fun supportsSimultaneousRendering(project: Project, file: VirtualFile): Boolean =
      CodeInsightContextManager.getInstance(project).withOwnerOf(file) { it.supportsSimultaneousRendering() } ?: true

    /** `true` when the daemon finished the analysis of [document] in [context]. Any thread, no lock. */
    fun isAnalyzed(project: Project, document: Document, context: CodeInsightContext): Boolean =
      DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap.allDirtyScopesAreNullFor(document, context)
  }
}
