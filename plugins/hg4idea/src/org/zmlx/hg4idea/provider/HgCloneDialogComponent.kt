// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider

import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsCloneDialogComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import org.zmlx.hg4idea.HgNotificationIdsHolder.Companion.CLONE_DESTINATION_ERROR
import org.zmlx.hg4idea.HgRememberedInputs
import org.zmlx.hg4idea.util.HgUtil
import java.nio.file.Paths

class HgCloneDialogComponent(project: Project, dialogStateListener: VcsCloneDialogComponentStateListener) :
  DvcsCloneDialogComponent(project,
                           HgUtil.DOT_HG,
                           HgRememberedInputs.getInstance(),
                           dialogStateListener) {
  private val LOG = Logger.getInstance(HgCloneDialogComponent::class.java)

  override fun doClone(listener: CheckoutProvider.Listener) {
    val validationInfo = CloneDvcsValidationUtils.createDestination(getDirectory())
    if (validationInfo != null) {
      LOG.error("Unable to create destination directory", validationInfo.message)
      VcsNotifier.getInstance(project).notifyError(CLONE_DESTINATION_ERROR,
                                                   VcsBundle.message("clone.dialog.clone.failed.error"),
                                                   VcsBundle.message("clone.dialog.unable.create.destination.error"))
      return
    }

    val directory = Paths.get(getDirectory()).fileName.toString()
    val url = getUrl()
    val parent = Paths.get(getDirectory()).toAbsolutePath().parent.toAbsolutePath().toString()

    HgCheckoutProvider.doClone(project, listener, directory, url, parent)
    rememberedInputs.addUrl(url)
    rememberedInputs.cloneParentDir = parent
  }

  override fun onComponentSelected(dialogStateListener: VcsCloneDialogComponentStateListener) {
    updateOkActionState(dialogStateListener)
  }
}