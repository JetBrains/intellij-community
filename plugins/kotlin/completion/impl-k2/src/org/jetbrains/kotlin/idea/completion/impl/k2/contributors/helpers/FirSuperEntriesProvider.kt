// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.nextLeafs
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.lookups.factories.insertAndShortenReferencesInStringUsingTemporarySuffix
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object FirSuperEntriesProvider {
    context(KaSession)
    fun getSuperClassesAvailableForSuperCall(context: PsiElement): List<KaNamedClassSymbol> {
        val containingClass = context.getStrictParentOfType<KtClassOrObject>() ?: return emptyList()
        val containingClassSymbol = containingClass.classSymbol ?: return emptyList()
        return containingClassSymbol.superTypes.mapNotNull { superType ->
            val classType = superType as? KaClassType ?: return@mapNotNull null
            classType.symbol as? KaNamedClassSymbol
        }
    }
}

@Serializable
internal object SuperCallInsertionHandler : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupObject = item.`object` as SuperCallLookupObject

        replaceWithClassIdAndShorten(lookupObject, context)
        if (context.completionChar != '.') context.insertStringAndInvokeCompletion(stringToInsert = ".")
    }

    private fun replaceWithClassIdAndShorten(
        lookupObject: SuperCallLookupObject,
        context: InsertionContext
    ) {
        val replaceTo = lookupObject.replaceTo ?: return
        context.tailOffset = getCorrectedTailOffsetForSuperCall(context) ?: context.tailOffset

        if (lookupObject.shortenReferencesInReplaced) {
            context.insertAndShortenReferencesInStringUsingTemporarySuffix(replaceTo)
        } else {
            context.document.replaceString(context.startOffset, context.tailOffset, replaceTo)
            context.commitDocument()
        }
    }

    /**
     * Returns corrected offset in case there is an existing super type qualifier, which should be replaced with the new one.
     * For example, in `super<caret><A>.foo()` the corrected offset is the end offset of the dot.
     */
    private fun getCorrectedTailOffsetForSuperCall(context: InsertionContext): Int? {
        val tokenAtPosition = context.file.findElementAt(context.startOffset) ?: return null

        val gtToken = tokenAtPosition.skipTokensAndGetExpectedTokenOrNull(TOKENS_BEFORE_CLOSING_ANGLE_BRACKET, KtTokens.GT)
        val dotToken = gtToken?.skipTokensAndGetExpectedTokenOrNull(KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET, KtTokens.DOT)

        return dotToken?.endOffset ?: gtToken?.endOffset
    }

    private fun PsiElement.skipTokensAndGetExpectedTokenOrNull(tokensToSkip: TokenSet, expectedToken: KtSingleValueToken): PsiElement? =
        nextLeafs.firstOrNull { it.node.elementType !in tokensToSkip }?.takeIf { it.node.elementType == expectedToken }

    private val TOKENS_BEFORE_CLOSING_ANGLE_BRACKET = TokenSet.orSet(
        TokenSet.create(KtTokens.IDENTIFIER, KtTokens.DOT, KtTokens.LT),
        KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET
    )
}

internal interface SuperCallLookupObject {
    val replaceTo: String?
    val shortenReferencesInReplaced: Boolean
}