// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil.wrapInHtml
import com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag
import git4idea.i18n.GitBundle
import git4idea.merge.GitConflictResolver
import git4idea.merge.GitDefaultMergeDialogCustomizer
import org.jetbrains.annotations.Nls

internal class GitApplyChangesConflictResolver(
  project: Project,
  root: VirtualFile,
  commitHash: String,
  commitAuthor: String,
  commitMessage: String,
  @Nls operationName: String,
) : GitConflictResolver(project, setOf(root),
                        createGitConflictResolverParams(project, commitHash, commitAuthor, commitMessage, operationName)) {
  override fun notifyUnresolvedRemain() {
  /* we show a [possibly] compound notification after applying all commits.*/
  }
}

private class MergeDialogCustomizer(
  project: Project,
  @NlsSafe private val commitHash: String,
  private val commitAuthor: String,
  @NlsSafe private val commitMessage: String,
  @Nls private val operationName: String,
) : GitDefaultMergeDialogCustomizer(project) {

  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>) = wrapInHtml(
    GitBundle.message(
      "apply.conflict.dialog.description.label.text",
      operationName,
      wrapInHtmlTag(commitHash, "code"),
      commitAuthor,
      UIUtil.BR + wrapInHtmlTag(commitMessage, "code")
    )
  )
}

private fun createGitConflictResolverParams(
  project: Project,
  commitHash: String,
  commitAuthor: String,
  commitMessage: String,
  @Nls operationName: String,
) = GitConflictResolver.Params(project).apply {
  setErrorNotificationTitle(GitBundle.message("apply.changes.operation.performed.with.conflicts", operationName.capitalize()))
  setMergeDialogCustomizer(MergeDialogCustomizer(project, commitHash, commitAuthor, commitMessage, operationName))
}