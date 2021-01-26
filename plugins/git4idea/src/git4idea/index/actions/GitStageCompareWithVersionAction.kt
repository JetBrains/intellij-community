// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import git4idea.index.*
import git4idea.index.vfs.GitIndexVirtualFile
import git4idea.index.vfs.filePath

abstract class GitStageCompareWithVersionAction(val currentVersion: ContentVersion, val compareWithVersion: ContentVersion) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || !isStagingAreaAvailable(project) || file == null
        || (file is GitIndexVirtualFile != (currentVersion == ContentVersion.STAGED))) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val status = GitStageTracker.getInstance(project).status(file)
    e.presentation.isVisible = (status != null)
    e.presentation.isEnabled = (status?.has(compareWithVersion) == true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    val root = getRoot(project, file) ?: return
    val status = GitStageTracker.getInstance(project).status(root, file) ?: return

    val producer = SimpleDiffRequestProducer.create(file.filePath(), ThrowableComputable {
      createDiffRequest(project, root, status)
    })
    DiffManager.getInstance().showDiff(e.project, SimpleDiffRequestChain.fromProducer(producer), DiffDialogHints.DEFAULT)
  }

  abstract fun createDiffRequest(project: Project, root: VirtualFile, status: GitFileStatus) : DiffRequest
}

class GitStageCompareLocalWithStagedAction: GitStageCompareWithVersionAction(ContentVersion.LOCAL, ContentVersion.STAGED) {
  override fun createDiffRequest(project: Project, root: VirtualFile, status: GitFileStatus): DiffRequest {
    return compareStagedWithLocal(project, root, status)
  }
}

class GitStageCompareStagedWithLocalAction: GitStageCompareWithVersionAction(ContentVersion.STAGED, ContentVersion.LOCAL) {
  override fun createDiffRequest(project: Project, root: VirtualFile, status: GitFileStatus): DiffRequest {
    return compareStagedWithLocal(project, root, status)
  }
}

class GitStageCompareStagedWithHeadAction: GitStageCompareWithVersionAction(ContentVersion.STAGED, ContentVersion.HEAD) {
  override fun createDiffRequest(project: Project, root: VirtualFile, status: GitFileStatus): DiffRequest {
    return compareHeadWithStaged(project, root, status)
  }
}