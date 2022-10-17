// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.actions

import com.intellij.copyright.CopyrightBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCopyrightCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return UpdateCopyrightCheckinHandler(panel.project)
  }
}

private class UpdateCopyrightCheckinHandler(val project: Project) : CheckinHandler(), CommitCheck {

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    return BooleanCommitOption(project, CopyrightBundle.message("before.checkin.update.copyright"), false,
                               settings::UPDATE_COPYRIGHT)
  }

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override fun isEnabled(): Boolean = settings.UPDATE_COPYRIGHT

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    withContext(Dispatchers.Default) {
      runUnderIndicator {
        val psiFiles = runReadAction { getPsiFiles(commitInfo.committedVirtualFiles) }
        UpdateCopyrightProcessor(project, null, psiFiles, false).run()
      }
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    return null
  }

  private val settings: UpdateCopyrightCheckinHandlerState get() = UpdateCopyrightCheckinHandlerState.getInstance(project)

  private fun getPsiFiles(files: List<VirtualFile>): Array<PsiFile> {
    val psiFiles = mutableListOf<PsiFile>()
    val manager = PsiManager.getInstance(project)
    for (file in files) {
      val psiFile = manager.findFile(file)
      if (psiFile != null) {
        psiFiles.add(psiFile)
      }
    }
    return PsiUtilCore.toPsiFileArray(psiFiles)
  }
}