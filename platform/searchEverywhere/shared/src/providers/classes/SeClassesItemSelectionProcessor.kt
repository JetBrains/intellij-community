// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.classes

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereNavigationHandler
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.intellij.platform.searchEverywhere.providers.target.selection.SeTargetItemSelectionProcessor

/**
 * Navigates a Classes result to the member of a `Foo#bar` query.
 */
internal class SeClassesItemSelectionProcessor : SeTargetItemSelectionProcessor {
  override suspend fun process(item: SeItem, provider: SeItemsProvider, modifiers: Int, searchText: String): Boolean? {
    if (provider.id != SeProviderIdUtils.CLASSES_ID) return null

    val rawItem = (item as? SeTargetPresentableItem)?.rawObject ?: return null

    // An item without PSI carries no member, so the default processor navigates it.
    val psiElement = readAction {
      PSIPresentationBgRendererWrapper.toPsi(rawItem)?.takeIf { it.isValid }
    } ?: return null

    ClassSearchEverywhereNavigationHandler(psiElement.project).gotoSelectedItem(psiElement, modifiers, searchText)
    return true
  }
}
