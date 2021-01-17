/*
 * Copyright 2000-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * @see NativePsiAndKtLightElementEqualityProviderTest
 */
class KtSearchEverywhereEqualityProvider : SEResultsEqualityProvider {
    override fun compareItems(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        return compareNativePsiAndKtLightElement(newItem, alreadyFoundItems)
    }

    private fun compareNativePsiAndKtLightElement(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        val newItemKt = newItem.toKtElement() ?: return DoNothing
        return alreadyFoundItems
            .map { alreadyFoundItem ->
                val alreadyFoundItemKt = alreadyFoundItem.toKtElement() ?: return@map DoNothing
                if (PsiManager.getInstance(newItemKt.project).areElementsEquivalent(newItemKt, alreadyFoundItemKt)) {
                    // Prefer to show native Kotlin psi elements
                    if (newItem.element is KtElement && alreadyFoundItem.element !is KtElement) Replace(alreadyFoundItem)
                    else Skip
                } else DoNothing
            }
            .reduceOrNull { acc, actionType -> acc.combine(actionType) }
            ?: DoNothing
    }
}

private fun SearchEverywhereFoundElementInfo.toKtElement(): KtElement? {
    val elem = element
    if (elem is KtElement) {
        return elem
    }
    if (elem is KtLightElement<*, *>) {
        return elem.kotlinOrigin
    }
    return null
}
