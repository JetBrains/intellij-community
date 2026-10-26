// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendBundle
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeApiKeyKind
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeApiKeyStore
import com.intellij.platform.searchEverywhere.frontend.providers.actions.isValidApiKey
import kotlinx.coroutines.launch

/**
 * Sets or removes one personal API key of a Search Everywhere ranker. An empty value removes the key.
 *
 * [kind] names the texts of the dialog, and [keyStore] keeps the key. See [SeApiKeyStore].
 */
internal abstract class SeSetApiKeyAction(
  private val kind: SeApiKeyKind,
  private val keyStore: suspend () -> SeApiKeyStore,
) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val bundle = SearchEverywhereFrontendBundle.bundle
    val key = Messages.showPasswordDialog(e.project,
                                          bundle.getMessage(kind.dialogMessageKey),
                                          bundle.getMessage(kind.dialogTitleKey),
                                          null,
                                          SeApiKeyValidator) ?: return
    e.coroutineScope.launch {
      keyStore().setKey(key)
    }
  }
}

/** Accepts a key that can go into an HTTP header, or an empty value that removes the key. */
private object SeApiKeyValidator : InputValidatorEx {
  override fun getErrorText(inputString: String): String? {
    val key = inputString.trim()
    if (key.isEmpty() || isValidApiKey(key)) return null
    return SearchEverywhereFrontendBundle.bundle.getMessage("search.everywhere.api.key.dialog.invalid")
  }

  override fun canClose(inputString: String): Boolean = checkInput(inputString)
}
