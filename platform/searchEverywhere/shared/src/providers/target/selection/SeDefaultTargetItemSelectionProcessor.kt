// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target.selection

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereNavigationContributionHandler
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereNavigationHandler
import com.intellij.ide.util.EditSourceUtil
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The fallback [SeTargetItemSelectionProcessor]. It navigates to the selected target, so it carries
 * `order="last"`.
 */
internal class SeDefaultTargetItemSelectionProcessor : SeTargetItemSelectionProcessor {
  override suspend fun process(item: SeItem, provider: SeItemsProvider, modifiers: Int, searchText: String): Boolean? {
    val rawItem = (item as? SeTargetPresentableItem)?.rawObject ?: return null

    val psiElement = readAction {
      PSIPresentationBgRendererWrapper.toPsi(rawItem)?.takeIf { it.isValid }
    }
    if (psiElement != null) {
      // The handler launches the navigation on its own scope, so this returns before the file opens.
      navigationHandler(psiElement).gotoSelectedItem(psiElement, modifiers, searchText)
      return true
    }

    val navigationItem = rawItem as? NavigationItem ?: return null
    withContext(Dispatchers.EDT) {
      WriteIntentReadAction.run {
        EditSourceUtil.navigate(navigationItem, true, false)
      }
    }
    return true
  }

  private fun navigationHandler(psiElement: PsiElement): SearchEverywhereNavigationHandler =
    if (psiElement is PsiFile) FileSearchEverywhereNavigationContributionHandler(psiElement.project)
    else SearchEverywhereNavigationHandler(psiElement.project)
}
