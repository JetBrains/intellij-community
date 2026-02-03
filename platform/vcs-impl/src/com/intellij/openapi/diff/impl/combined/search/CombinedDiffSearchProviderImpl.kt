// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.combined.search

import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.diff.tools.combined.currentEditor
import com.intellij.diff.tools.combined.search.CombinedDiffSearchContext
import com.intellij.diff.tools.combined.search.CombinedDiffSearchContext.EditorHolder
import com.intellij.diff.tools.combined.search.CombinedDiffSearchController
import com.intellij.diff.tools.combined.search.CombinedDiffSearchProvider
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

internal class CombinedDiffSearchProviderImpl(private val project: Project) : CombinedDiffSearchProvider {

  override fun installSearch(viewer: CombinedDiffViewer) {
    val mainUI = viewer.getMainUI()
    val currentEditor = viewer.getCurrentDiffViewer()?.currentEditor ?: return

    val searchController = CombinedDiffSearchControllerImpl(project, currentEditor, viewer, mainUI::closeSearch, mainUI.getComponent())

    mainUI.setSearchController(searchController)

    searchController.session.component.requestFocusInTheSearchFieldAndSelectContent(project)
  }

  private class CombinedDiffSearchControllerImpl(project: Project,
                                                 currentEditor: Editor,
                                                 private val viewer: CombinedDiffViewer,
                                                 closeAction: () -> Unit,
                                                 parentComponent: JComponent) :
    CombinedDiffSearchController, CombinedEditorSearchSessionListener {

    val session = CombinedEditorSearchSession(project, currentEditor, closeAction, parentComponent, viewer)
      .also { it.addListener(this) }

    init {
      session.update(viewer.createSearchContext().holders, EditorHolder::editors, currentEditor)
    }

    override val searchComponent: JComponent
      get() = session.component

    override fun update(context: CombinedDiffSearchContext) {
      session.update(context.holders, EditorHolder::editors)
    }

    override fun onSearch(forward: Boolean, editor: Editor) {
      viewer.scrollToEditor(editor, !isSearchComponentInFocus())
    }

    override fun statusTextChanged(matches: Int, files: Int) {
      val totalFilesCount = viewer.getDiffBlocksCount()
      val statusKey = if (totalFilesCount != files) "diff.files.editors.search.partly.matches" else "diff.files.editors.search.matches"

      session.setStatusText(DiffBundle.message(statusKey, matches, DiffBundle.message("diff.files.editors.search.files.count", files)))
    }

    private fun isSearchComponentInFocus(): Boolean = UIUtil.isFocusAncestor(session.component)
  }
}
