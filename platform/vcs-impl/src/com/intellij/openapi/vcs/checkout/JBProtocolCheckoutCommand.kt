// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout

import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.CheckoutProviderEx
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.AppIcon

/**
 * @author Konstantin Bulenkov
 */
internal class JBProtocolCheckoutCommand : JBProtocolCommand("checkout") {
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? {
    val repository = parameter(parameters, "checkout.repo")
    val provider = CheckoutProvider.EXTENSION_POINT_NAME.findFirstSafe { it is CheckoutProviderEx && it.vcsId == target } as CheckoutProviderEx?
                   ?: return VcsBundle.message("jb.protocol.no.provider", target)
    val project = ProjectManager.getInstance().defaultProject
    val listener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener
    AppIcon.getInstance().requestAttention(null, true)
    provider.doCheckout(project, listener, repository)
    return null
  }
}