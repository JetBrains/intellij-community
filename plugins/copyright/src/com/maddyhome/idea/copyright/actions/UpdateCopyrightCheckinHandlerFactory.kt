// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.actions

import com.intellij.copyright.CopyrightBundle
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinModificationHandler
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.PairConsumer

class UpdateCopyrightCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return UpdateCopyrightCheckinHandler(panel)
  }

  private class UpdateCopyrightCheckinHandler(private val panel: CheckinProjectPanel) : CheckinHandler(), CheckinModificationHandler {

    override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
      return BooleanCommitOption(panel, CopyrightBundle.message("before.checkin.update.copyright"), false,
                                 { settings.UPDATE_COPYRIGHT }, { value -> settings.UPDATE_COPYRIGHT = value })
    }

    override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult? {
      if (settings.UPDATE_COPYRIGHT) {
        UpdateCopyrightProcessor(panel.project, null, getPsiFiles()).run()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
      return super.beforeCheckin()
    }

    private val settings: UpdateCopyrightCheckinHandlerState get() = UpdateCopyrightCheckinHandlerState.getInstance(panel.project)

    private fun getPsiFiles(): Array<PsiFile> {
      val files = panel.virtualFiles
      val psiFiles: MutableList<PsiFile> = ArrayList()
      val manager = PsiManager.getInstance(panel.project)
      for (file in files) {
        val psiFile = manager.findFile(file)
        if (psiFile != null) {
          psiFiles.add(psiFile)
        }
      }
      return PsiUtilCore.toPsiFileArray(psiFiles)
    }
  }
}