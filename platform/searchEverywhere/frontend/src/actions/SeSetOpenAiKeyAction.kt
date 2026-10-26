// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendBundle
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeOpenAiKeyStore
import com.intellij.platform.searchEverywhere.frontend.providers.actions.isValidOpenAiKey
import kotlinx.coroutines.launch

/**
 * Sets or removes the personal OpenAI API key of the Decision API ranker of Search Everywhere.
 * An empty value removes the key. See [SeOpenAiKeyStore].
 */
internal class SeSetOpenAiKeyAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val bundle = SearchEverywhereFrontendBundle.bundle
    val key = Messages.showPasswordDialog(e.project,
                                          bundle.getMessage("search.everywhere.openai.key.dialog.message"),
                                          bundle.getMessage("search.everywhere.openai.key.dialog.title"),
                                          null,
                                          SeOpenAiKeyValidator) ?: return
    e.coroutineScope.launch {
      serviceAsync<SeOpenAiKeyStore>().setKey(key)
    }
  }
}

/** Accepts a key that can go into an HTTP header, or an empty value that removes the key. */
private object SeOpenAiKeyValidator : InputValidatorEx {
  override fun getErrorText(inputString: String): String? {
    val key = inputString.trim()
    if (key.isEmpty() || isValidOpenAiKey(key)) return null
    return SearchEverywhereFrontendBundle.bundle.getMessage("search.everywhere.openai.key.dialog.invalid")
  }

  override fun canClose(inputString: String): Boolean = checkInput(inputString)
}
