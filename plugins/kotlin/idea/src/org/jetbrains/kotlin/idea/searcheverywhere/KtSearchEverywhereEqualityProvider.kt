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
        fun <T> reduce(
            transformation: SearchEverywhereFoundElementInfo.() -> T?,
            isEquivalent: (new: T, old: T) -> Boolean,
            shouldBeReplaced: (new: T, old: T) -> Boolean,
        ): SEEqualElementsActionType? {
            val transformedNewItem = newItem.transformation() ?: return null
            return alreadyFoundItems
                .asSequence()
                .mapNotNull { alreadyFoundItem ->
                    val transformedOldItem = alreadyFoundItem.transformation() ?: return@mapNotNull null
                    if (!isEquivalent(transformedNewItem, transformedOldItem)) return@mapNotNull null
                    if (shouldBeReplaced(transformedNewItem, transformedOldItem)) Replace(alreadyFoundItem) else Skip
                }
                .reduceOrNull { acc, actionType -> acc.combine(actionType) }
        }

        return reduce(
            transformation = { toPsi() },
            isEquivalent = { t, old -> PsiManager.getInstance(t.project).areElementsEquivalent(t, old) },
            shouldBeReplaced = { new, old -> new is KtElement && old !is KtElement },
        ) ?: reduce(
            transformation = { toPsi()?.let { it.unwrapped ?: it }?.withKind() },
            isEquivalent = { t, old -> getGroupLeader(t.first)?.equals(getGroupLeader(old.first)) == true },
            shouldBeReplaced = { new, old -> minOf(new, old, compareBy { it.second }) === new },
        ) ?: DoNothing
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
