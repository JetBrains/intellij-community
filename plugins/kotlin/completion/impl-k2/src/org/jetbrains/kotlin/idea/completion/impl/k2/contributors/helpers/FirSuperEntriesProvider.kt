// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtClassType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object FirSuperEntriesProvider {
    fun KtAnalysisSession.getSuperClassesAvailableForSuperCall(context: PsiElement): List<KtNamedClassOrObjectSymbol> {
        val containingClass = context.getStrictParentOfType<KtClassOrObject>() ?: return emptyList()
        val containingClassSymbol = containingClass.getClassOrObjectSymbol() ?: return emptyList()
        return containingClassSymbol.superTypes.mapNotNull { superType ->
            val classType = superType as? KtNonErrorClassType ?: return@mapNotNull null
            classType.classSymbol as? KtNamedClassOrObjectSymbol
        }
    }
}

internal object SuperCallInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupObject = item.`object` as SuperCallLookupObject

        replaceWithClassIdAndShorten(lookupObject, context)
        context.insertSymbolAndInvokeCompletion(symbol = ".")
    }

    private fun replaceWithClassIdAndShorten(
        lookupObject: SuperCallLookupObject,
        context: InsertionContext
    ) {
        val replaceTo = lookupObject.replaceTo ?: return
        context.document.replaceString(context.startOffset, context.tailOffset, replaceTo)
        context.commitDocument()

        if (lookupObject.shortenReferencesInReplaced) {
            val targetFile = context.file as KtFile
            shortenReferencesInRange(targetFile, TextRange(context.startOffset, context.tailOffset))
        }
    }
}

internal interface SuperCallLookupObject {
    val replaceTo: String?
    val shortenReferencesInReplaced: Boolean
}