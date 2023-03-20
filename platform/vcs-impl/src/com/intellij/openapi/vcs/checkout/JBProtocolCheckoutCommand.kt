// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.CheckoutProviderEx
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.AppIcon
import com.intellij.util.ui.cloneDialog.VcsCloneDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author Konstantin Bulenkov
 */
internal class JBProtocolCheckoutCommand : JBProtocolCommand("checkout") {
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? {
    val repository = parameter(parameters, "checkout.repo")
    val providerClass =
      CheckoutProvider.EXTENSION_POINT_NAME.findFirstSafe { it is CheckoutProviderEx && it.vcsId == target }?.javaClass
      ?: return VcsBundle.message("jb.protocol.no.provider", target)

    withContext(Dispatchers.EDT) {
      val project = ProjectManager.getInstance().defaultProject
      val listener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener
      AppIcon.getInstance().requestAttention(null, true)
      val dialog = VcsCloneDialog.Builder(project).forVcs(providerClass, repository)
      if (dialog.showAndGet()) {
        dialog.doClone(listener)
      }
    }
    return null
  }
}