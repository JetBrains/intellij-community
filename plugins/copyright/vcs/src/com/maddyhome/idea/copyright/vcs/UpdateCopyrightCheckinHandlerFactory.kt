// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.maddyhome.idea.copyright.vcs

import com.intellij.copyright.CopyrightBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.withProgressText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.maddyhome.idea.copyright.actions.UpdateCopyrightCheckinHandlerState
import com.maddyhome.idea.copyright.actions.UpdateCopyrightProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class UpdateCopyrightCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return UpdateCopyrightCheckinHandler(panel.project)
  }
}

private class UpdateCopyrightCheckinHandler(private val project: Project) : CheckinHandler(), CommitCheck {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
    return BooleanCommitOption.create(project, this, disableWhenDumb = false,
                                      CopyrightBundle.message("before.checkin.update.copyright"),
                                      settings::UPDATE_COPYRIGHT)
  }

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override fun isEnabled(): Boolean = settings.UPDATE_COPYRIGHT

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val files = commitInfo.committedVirtualFiles
    withProgressText(CopyrightBundle.message("updating.copyrights.progress.message")) {
      withContext(Dispatchers.Default) {
        val psiFiles = readAction { getPsiFiles(files) }
        coroutineToIndicator {
          UpdateCopyrightProcessor(project, null, psiFiles, false).run()
        }
      }
    }
    writeIntentReadAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
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
