// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestProducer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.ThrowableComputable
import git4idea.index.*
import git4idea.index.vfs.filePath

class GitStageCompareThreeVersionsAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || !isStagingAreaAvailable(project) || file == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val status = GitStageTracker.getInstance(project).status(file)
    e.presentation.isVisible = status != null
    e.presentation.isEnabled = status?.has(ContentVersion.HEAD) == true &&
                               status.has(ContentVersion.STAGED) &&
                               status.has(ContentVersion.LOCAL)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    val root = getRoot(project, file) ?: return
    val status = GitStageTracker.getInstance(project).status(root, file) ?: return
    val producer = SimpleDiffRequestProducer.create(file.filePath(), ThrowableComputable {
      compareThreeVersions(project, root, status)
    })
    DiffManager.getInstance().showDiff(e.project, SimpleDiffRequestChain.fromProducer(producer), DiffDialogHints.DEFAULT)
  }
}