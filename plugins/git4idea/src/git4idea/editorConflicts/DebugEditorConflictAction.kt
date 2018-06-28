// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.impl.VcsLogContentUtil
import git4idea.history.GitHistoryUtils

private val LOG = Logger.getInstance("git4idea.editorConflicts.DebugEditorConflictAction")

class DebugEditorConflictAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val selection = editor.selectionModel.selectedText ?: return
    val commitish = commitishBySelection(selection, editor, project)
//    project.putUserData(EditorConflictSupport.ACTIVE_CONFLICT_MARKER, commitish)
    if (commitish == null) {
      LOG.warn("Cant find commit by \"${selection}\"")
      return
    }
    navigateToCommitish(project, commitish)
  }

  private fun commitishBySelection(selection: String, editor: Editor, project: Project): String? {
    if (selection == "merged common ancestors")
      return findRevisionForCommonAncestorMark(editor, project)
    else
      return selection
  }

  private fun findRevisionForCommonAncestorMark(editor: Editor, project: Project): String? {
    val root = project.baseDir
    val first = findClosestCommitishMark(editor, true)
    val second = findClosestCommitishMark(editor, false)
    val base = GitHistoryUtils.getMergeBase(project, root, first, second)
    LOG.warn("Found $base for $first and $second")
    return base?.rev
  }

  private fun findClosestCommitishMark(editor: Editor, forward: Boolean): String {
    // fixme
    return when (forward) {
      true -> "branch1"
      false -> "HEAD"
    }

  }

  private fun navigateToCommitish(project: Project, commitish: String) {
    VcsLogContentUtil.openMainLogAndExecute(project) { it.vcsLog.jumpToReference(commitish) }
  }
}