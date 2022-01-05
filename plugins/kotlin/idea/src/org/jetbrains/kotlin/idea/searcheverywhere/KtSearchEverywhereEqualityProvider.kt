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
        val newPsiElement = newItem.toPsi() ?: return DoNothing
        val result = compareElements(newPsiElement) {
            alreadyFoundItems.asSequence().mapNotNull { it.toPsi() }
        }

        return when (result) {
            null -> DoNothing
            -1 -> Skip
            else -> Replace(alreadyFoundItems[result])
        }
    }

    companion object {
        /**
         * @return
         * null if a rule is not applicable,
         * -1 if [newItem] should be skipped,
         * the index of [alreadyFoundItems] if an existent element should be replaced
         */
        fun compareElements(
            newItem: PsiElement,
            alreadyFoundItems: () -> Sequence<PsiElement>,
        ): Int? {
            fun <T> reduce(
                transformation: PsiElement.() -> T?,
                shouldBeProcessed: (new: T, old: T) -> Boolean,
                shouldBeReplaced: (new: T, old: T) -> Boolean,
            ): Int? {
                val transformedNewItem = newItem.transformation() ?: return null
                return alreadyFoundItems().mapIndexedNotNull(fun(index: Int, alreadyFoundItem: PsiElement): Int? {
                    val transformedOldItem = alreadyFoundItem.transformation() ?: return null
                    if (!shouldBeProcessed(transformedNewItem, transformedOldItem)) return null
                    return if (shouldBeReplaced(transformedNewItem, transformedOldItem)) index else -1
                }).firstOrNull()
            }

            return reduce(
                transformation = { this },
                shouldBeProcessed = { new, old ->
                    // [com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider] is responsible for "new == old" case
                    (new::class != old::class || new === old) && PsiManager.getInstance(new.project).areElementsEquivalent(new, old)
                },
                shouldBeReplaced = { new, old -> new is KtElement && old !is KtElement },
            ) ?: reduce(
                transformation = { (this.unwrapped ?: this).withKind() },
                shouldBeProcessed = { new, old ->
                    new.second != old.second && getGroupLeader(new.first)?.equals(getGroupLeader(old.first)) == true
                },
                shouldBeReplaced = { new, old -> minOf(new, old, compareBy { it.second }) === new },
            )
        }
    }
}

private fun getGroupLeader(element: PsiElement): KtFile? {
    if (element is KtFile) {
        return element
    }

    if (element is KtLightClassForFacade &&
        element.facadeClassFqName.shortName().asString().removeSuffix("Kt") == element.containingFile.virtualFile.nameWithoutExtension
    ) {
        return element.containingFile as? KtFile
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

private fun SearchEverywhereFoundElementInfo.toPsi() = PSIPresentationBgRendererWrapper.toPsi(element)

private enum class Kind {
    KtClass, KtFile, KtFacade
}

private fun PsiElement.withKind(): Pair<PsiElement, Kind>? = when (this) {
    is KtFile -> this to Kind.KtFile
    is KtClass -> this to Kind.KtClass
    is KtLightClassForFacade -> this to Kind.KtFacade
    else -> null
}
