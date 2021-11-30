// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * @see org.jetbrains.kotlin.idea.searcheverywhere.NativePsiAndKtLightElementEqualityProviderTest
 * @see org.jetbrains.kotlin.idea.searcheverywhere.KtSearchEverywhereEqualityProviderTest
 */
class KtSearchEverywhereEqualityProvider : SEResultsEqualityProvider {
    override fun compareItems(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        return compareNativePsiAndUltraLightClass(newItem, alreadyFoundItems).takeIf { it != DoNothing }
            ?: compareByPriority(newItem, alreadyFoundItems)
    }

    private fun compareByPriority(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        val newItemWithKind = newItem.toPsi()?.let { it.unwrapped ?: it }?.withKind() ?: return DoNothing
        return alreadyFoundItems
            .asSequence()
            .map { alreadyFoundItem ->
                val alreadyFoundItemWithKind = alreadyFoundItem.toPsi()?.let { it.unwrapped ?: it }?.withKind()
                    ?: return@map DoNothing
                if (getGroupLeader(newItemWithKind.first)?.equals(getGroupLeader(alreadyFoundItemWithKind.first)) == true) {
                    val winner = minOf(newItemWithKind, alreadyFoundItemWithKind, compareBy { it.second })
                    if (winner === newItemWithKind) Replace(alreadyFoundItem) else Skip
                } else {
                    DoNothing
                }
            }
            .reduceOrNull { acc, actionType -> acc.combine(actionType) }
            ?: DoNothing
    }

    private fun compareNativePsiAndUltraLightClass(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>
    ): SEEqualElementsActionType {
        val newItemPsi = newItem.toPsi() ?: return DoNothing
        return alreadyFoundItems
            .asSequence()
            .map { alreadyFoundItem ->
                val alreadyFoundItemPsi = alreadyFoundItem.toPsi() ?: return@map DoNothing
                if (PsiManager.getInstance(newItemPsi.project).areElementsEquivalent(newItemPsi, alreadyFoundItemPsi)) {
                    // Prefer to show native Kotlin psi elements
                    if (newItemPsi is KtElement && alreadyFoundItemPsi !is KtElement) Replace(alreadyFoundItem)
                    else Skip
                } else DoNothing
            }
            .reduceOrNull { acc, actionType -> acc.combine(actionType) }
            ?: DoNothing
    }

    private fun getGroupLeader(element: PsiElement): KtFile? {
        if (element is KtFile) {
            return element
        }
        if (element is KtLightClassForFacade &&
            element.fqName.shortName().asString().removeSuffix("Kt") == element.containingFile.virtualFile.nameWithoutExtension
        ) {
            return element.containingFile.ktFile
        }
        if (element is KtClass && element.isTopLevel()) {
            val file = element.parent
            if (file is KtFile &&
                element.name == file.virtualFile.nameWithoutExtension
            ) {
                return file
            }
        }
        return null
    }
}

private fun SearchEverywhereFoundElementInfo.toPsi() =
    PSIPresentationBgRendererWrapper.toPsi(element)

private enum class Kind {
    KtClass, KtFile, KtFacade
}

private fun PsiElement.withKind(): Pair<PsiElement, Kind>? = when (this) {
    is KtFile -> this to Kind.KtFile
    is KtClass -> this to Kind.KtClass
    is KtLightClassForFacade -> this to Kind.KtFacade
    else -> null
}
