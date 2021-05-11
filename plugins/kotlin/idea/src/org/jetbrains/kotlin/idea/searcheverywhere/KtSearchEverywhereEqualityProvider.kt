// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
 * Q: Why is [KtSearchEverywhereEqualityProvider] implemented as bunch of methods but not as bunch of extension points?
 * A: Because we want to make sure that "native Psi vs KtLightElement" is checked first
 *
 * @see NativePsiAndKtLightElementEqualityProviderTest
 * @see KtFileAndKtClassEqualityProviderTest
 * @see KtFileAndKtClassForFacadeTest
 */
class KtSearchEverywhereEqualityProvider : SEResultsEqualityProvider {
    override fun compareItems(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        return compareNativePsiAndKtLightElement(newItem, alreadyFoundItems).takeIf { it != DoNothing }
            ?: compareKtFileAndKtClassForFacade(newItem, alreadyFoundItems).takeIf { it != DoNothing }
            ?: compareKtFileAndKtClass(newItem, alreadyFoundItems)
    }

    private fun compareKtFileAndKtClass(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        val newItemKt = newItem.toKtElement() ?: return DoNothing
        return alreadyFoundItems
            .map { alreadyFoundItem ->
                val alreadyFoundItemKt = alreadyFoundItem.toKtElement() ?: return@map DoNothing

                val (klass, file) = when {
                    newItemKt is KtClass && alreadyFoundItemKt is KtFile -> newItemKt to alreadyFoundItemKt
                    alreadyFoundItemKt is KtClass && newItemKt is KtFile -> alreadyFoundItemKt to newItemKt
                    else -> return@map DoNothing
                }

                if (klass.isTopLevel() && klass.parent == file && file.findFacadeClass() == null) {
                    // Prefer to show classes over files
                    if (newItemKt == klass) Replace(alreadyFoundItem) else Skip
                } else DoNothing
            }
            .reduceOrNull { acc, actionType -> acc.combine(actionType) }
            ?: DoNothing
    }

    private fun compareKtFileAndKtClassForFacade(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        val newItemKt = newItem.toKtElement()
        val newItemElem = newItem.element
        return alreadyFoundItems
            .map { alreadyFoundItem ->
                val alreadyFoundItemKt = alreadyFoundItem.toKtElement()
                val alreadyFoundItemElem = alreadyFoundItem.element

                val (file, classForFacade) = when {
                    newItemKt is KtFile && alreadyFoundItemElem is KtLightClassForFacade -> newItemKt to alreadyFoundItemElem
                    alreadyFoundItemKt is KtFile && newItemElem is KtLightClassForFacade -> alreadyFoundItemKt to newItemElem
                    else -> return@map DoNothing
                }

                if (
                    PsiManager.getInstance(file.project).areElementsEquivalent(classForFacade.files.singleOrNull(), file) &&
                    classForFacade.facadeClassFqName.shortName().asString().removeSuffix("Kt") == file.virtualFile.nameWithoutExtension
                ) {
                    if (SearchEverywhereFoundElementInfo.COMPARATOR.compare(newItem, alreadyFoundItem) > 0) Replace(alreadyFoundItem)
                    else Skip
                } else DoNothing
            }
            .reduceOrNull { acc, actionType -> acc.combine(actionType) }
            ?: DoNothing
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
