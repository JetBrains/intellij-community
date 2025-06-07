// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.math.max

@ApiStatus.Internal
abstract class AbstractCompletionDummyIdentifierProviderService : CompletionDummyIdentifierProviderService {
    override fun correctPositionForStringTemplateEntry(context: CompletionInitializationContext): Boolean {
        val offset = context.startOffset
        val psiFile = context.file
        val tokenBefore = psiFile.findElementAt(max(0, offset - 1))

        if (offset > 0 && tokenBefore!!.node.elementType == KtTokens.REGULAR_STRING_PART && tokenBefore.text.startsWith(".")) {
            val prev = tokenBefore.parent.prevSibling
            if (prev != null && prev is KtSimpleNameStringTemplateEntry) {
                val expression = prev.expression
                if (expression != null) {
                    val prefix = tokenBefore.text.substring(0, offset - tokenBefore.startOffset)
                    context.dummyIdentifier = "{" + expression.text + prefix + CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED + "}"
                    context.offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, expression.startOffset)
                    return true
                }
            }
        }
        return false
    }

    override fun correctPositionForParameter(context: CompletionInitializationContext) {
        val offset = context.startOffset
        val psiFile = context.file
        val tokenAt = psiFile.findElementAt(max(0, offset)) ?: return

        // IDENTIFIER when 'f<caret>oo: Foo'
        // COLON when 'foo<caret>: Foo'
        if (tokenAt.node.elementType == KtTokens.IDENTIFIER || tokenAt.node.elementType == KtTokens.COLON) {
            val parameter = tokenAt.parent as? KtParameter
            if (parameter != null) {
                context.replacementOffset = parameter.endOffset
            }
        }
    }

    override fun provideDummyIdentifier(context: CompletionInitializationContext): String {
        val psiFile = context.file
        if (psiFile !is KtFile) {
            error("CompletionDummyIdentifierProviderService.providerDummyIdentifier should not be called for non KtFile")
        }

        val offset = context.startOffset
        val tokenBefore = psiFile.findElementAt(max(0, offset - 1))

        val suffix = when {
            tokenBefore == null || context.completionType == CompletionType.SMART -> DEFAULT_PARSING_BREAKER
            else -> provideSuffixToAffectParsingIfNecessary(tokenBefore)
        }

        return CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED + suffix
    }

    override fun provideSuffixToAffectParsingIfNecessary(element: PsiElement): String = when {
        // TODO package completion

        isInClassHeader(element) -> EMPTY_SUFFIX // do not add '$' to not interrupt class declaration parsing

        isInUnclosedSuperQualifier(element) -> ">"

        isInSimpleStringTemplate(element) -> EMPTY_SUFFIX

        else -> specialLambdaSignatureDummyIdentifierSuffix(element)
            ?: specialExtensionReceiverDummyIdentifierSuffix(element)
            ?: specialInTypeArgsDummyIdentifierSuffix(element)
            ?: specialInArgumentListDummyIdentifierSuffix(element)
            ?: specialInNameWithQuotes(element)
            ?: specialInBinaryExpressionDummyIdentifierSuffix(element)
            ?: isInValueOrTypeParametersList(element)
            ?: handleDefaultCase(element)
            ?: isInAnnotationEntry(element)
            ?: isInEndOfTypeReference(element)
            ?: isInEndOfStatement(element)
            ?: isBeforeTypeBrackets(element)
            ?: isInKDocName(element)
            ?: DEFAULT_PARSING_BREAKER
    }

    /**
     * In the following cases, the elements at caret hold different expectations:
     * * `@A<caret>.B` - `A` can be regular class, object, interface;
     * * `@A<caret>` - `A` can be annotation class only.
     *
     * To complete element from the first example as though there is no `.B` after,
     * we need to add parsing breaker to the dummy identifier.
     */
    private fun isInAnnotationEntry(tokenBefore: PsiElement): String? {
        val typeReference = tokenBefore.parentOfType<KtTypeReference>(true) ?: return null
        return if (typeReference.parentOfType<KtAnnotationEntry>() != null) {
            DEFAULT_PARSING_BREAKER
        } else {
            null
        }
    }

    private fun isBeforeTypeBrackets(tokenBefore: PsiElement): String? {
        val referenceParent = tokenBefore.parent as? KtNameReferenceExpression ?: return null
        if (referenceParent.nextSibling is KtTypeArgumentList) return EMPTY_SUFFIX
        return null
    }

    private fun isInEndOfStatement(tokenBefore: PsiElement): String? {
        val blockExpression = tokenBefore.parentOfType<KtBlockExpression>(withSelf = true)
        if (blockExpression?.statements?.any { it.endOffset == tokenBefore.endOffset } == true) return EMPTY_SUFFIX
        return null
    }

    private fun isInEndOfTypeReference(tokenBefore: PsiElement): String? {
        val userType = (tokenBefore.parent as? KtNameReferenceExpression)?.parent as? KtUserType
        val typeReference = userType?.parentOfType<KtTypeReference>(withSelf = true)
        if (tokenBefore.endOffset == typeReference?.endOffset) return EMPTY_SUFFIX
        return null
    }

    private fun isInKDocName(tokenBefore: PsiElement): String? = if (tokenBefore.parent is KDocName) EMPTY_SUFFIX else null

    protected open fun handleDefaultCase(tokenBefore: PsiElement): String? = null

    private fun isInValueOrTypeParametersList(tokenBefore: PsiElement): String? {
        if (tokenBefore.parents.any { it is KtTypeParameterList || it is KtParameterList || it is KtContextReceiverList }) {
            return EMPTY_SUFFIX
        }
        return null
    }

    private fun specialLambdaSignatureDummyIdentifierSuffix(tokenBefore: PsiElement): String? {
        var leaf = tokenBefore
        while (leaf is PsiWhiteSpace || leaf is PsiComment) {
            leaf = leaf.prevLeaf(skipEmptyElements = true) ?: return null
        }

        val lambda = leaf.parents.firstOrNull { it is KtFunctionLiteral } ?: return null

        val lambdaChild = leaf.parents.takeWhile { it != lambda }.lastOrNull()

        return if (lambdaChild is KtParameterList)
            EMPTY_SUFFIX
        else
            null

    }

    private fun isInClassHeader(tokenBefore: PsiElement): Boolean {
        val classOrObject = tokenBefore.parents.firstIsInstanceOrNull<KtClassOrObject>() ?: return false
        val name = classOrObject.nameIdentifier ?: return false
        val headerEnd = classOrObject.body?.startOffset ?: classOrObject.endOffset
        val offset = tokenBefore.startOffset
        return name.endOffset <= offset && offset <= headerEnd
    }

    private fun specialInBinaryExpressionDummyIdentifierSuffix(tokenBefore: PsiElement): String? {
        if (tokenBefore.elementType == KtTokens.IDENTIFIER && tokenBefore.context?.context is KtBinaryExpression)
            return EMPTY_SUFFIX
        return null
    }

    private fun specialInNameWithQuotes(tokenBefore: PsiElement): String? {
        val badCharacterBefore = when (tokenBefore.elementType) {
            TokenType.BAD_CHARACTER -> tokenBefore
            KtTokens.IDENTIFIER -> tokenBefore.prevLeaf(skipEmptyElements = true)?.takeIf { it.elementType == TokenType.BAD_CHARACTER }
            else -> null
        }
        val quote = "`"
        if (badCharacterBefore?.text == quote) return "$quote$DEFAULT_PARSING_BREAKER"
        return null
    }

    private fun isInUnclosedSuperQualifier(tokenBefore: PsiElement): Boolean {
        val tokensToSkip = TokenSet.orSet(TokenSet.create(KtTokens.IDENTIFIER, KtTokens.DOT), KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET)
        val tokens = generateSequence(tokenBefore) { it.prevLeaf() }
        val ltToken = tokens.firstOrNull { it.node.elementType !in tokensToSkip } ?: return false
        if (ltToken.node.elementType != KtTokens.LT) return false
        val superToken = ltToken.prevLeaf { it !is PsiWhiteSpace && it !is PsiComment }
        return superToken?.node?.elementType == KtTokens.SUPER_KEYWORD
    }

    private fun isInSimpleStringTemplate(tokenBefore: PsiElement): Boolean {
        return tokenBefore.parents.firstIsInstanceOrNull<KtStringTemplateExpression>()?.isPlain() ?: false
    }


    private fun specialExtensionReceiverDummyIdentifierSuffix(tokenBefore: PsiElement): String? {
        if (tokenBefore.parentOfType<KtTypeReference>() != null) return null // already parsed as type reference

        var token = tokenBefore
        var ltCount = 0
        var gtCount = 0
        val builder = StringBuilder()
        while (true) {
            val tokenType = token.node!!.elementType
            if (tokenType in declarationKeywords) {
                val balance = ltCount - gtCount
                if (balance < 0) return null
                builder.append(token.text!!.reversed())
                builder.reverse()

                var tail = ">".repeat(balance) + ".f"
                if (tokenType == KtTokens.FUN_KEYWORD) {
                    tail += "()"
                }

                if (tokenBefore.elementType != KtTokens.IDENTIFIER) {
                    builder.append("X") // insert fake receiver before checking built declaration for the presence of error elements
                }
                builder.append(tail)

                val text = builder.toString()
                val file = KtPsiFactory(tokenBefore.project).createFile(text)
                val declaration = file.declarations.singleOrNull() ?: return null
                if (declaration.textLength != text.length) return null
                val containsErrorElement = !PsiTreeUtil.processElements(file) { it !is PsiErrorElement }
                return if (containsErrorElement) null else tail
            }
            if (tokenType !in typeArgumentsTokens) return null
            if (tokenType == KtTokens.LT) ltCount++
            if (tokenType == KtTokens.GT) gtCount++
            builder.append(token.text!!.reversed())
            token = PsiTreeUtil.prevLeaf(token) ?: return null
        }
    }

    private fun specialInTypeArgsDummyIdentifierSuffix(tokenBefore: PsiElement): String? {
        if (tokenBefore.getParentOfType<KtTypeArgumentList>(true) != null) { // already parsed inside type argument list
            return EMPTY_SUFFIX // do not insert '$' to not break type argument list parsing
        }

        val pair = unclosedTypeArgListNameAndBalance(tokenBefore) ?: return null
        val (nameToken, balance) = pair
        assert(balance > 0)

        val nameRef = nameToken.parent as? KtNameReferenceExpression ?: return null
        return if (allTargetsAreFunctionsOrClasses(nameRef)) {
            ">".repeat(balance)
        } else {
            null
        }
    }

    protected abstract fun allTargetsAreFunctionsOrClasses(nameReferenceExpression: KtNameReferenceExpression): Boolean

    private fun unclosedTypeArgListNameAndBalance(tokenBefore: PsiElement): Pair<PsiElement, Int>? {
        val nameToken = findCallNameTokenIfInTypeArgs(tokenBefore) ?: return null
        val pair = unclosedTypeArgListNameAndBalance(nameToken)
        return if (pair == null) {
            Pair(nameToken, 1)
        } else {
            Pair(pair.first, pair.second + 1)
        }
    }

    // if the leaf could be located inside type argument list of a call (if parsed properly)
    // then it returns the call name reference this type argument list would belong to
    private fun findCallNameTokenIfInTypeArgs(leaf: PsiElement): PsiElement? {
        var current = leaf
        while (true) {
            val tokenType = current.node!!.elementType
            if (tokenType !in typeArgumentsTokens) return null

            if (tokenType == KtTokens.LT) {
                val nameToken = current.prevLeaf(skipEmptyElements = true) ?: return null
                if (nameToken.node!!.elementType != KtTokens.IDENTIFIER) return null
                return nameToken
            }

            if (tokenType == KtTokens.GT) { // pass nested type argument list
                val prev = current.prevLeaf(skipEmptyElements = true) ?: return null
                val typeRef = findCallNameTokenIfInTypeArgs(prev) ?: return null
                current = typeRef
                continue
            }

            current = current.prevLeaf(skipEmptyElements = true) ?: return null
        }
    }

    private fun specialInArgumentListDummyIdentifierSuffix(tokenBefore: PsiElement): String? {
        // If we insert `$` in the argument list of a delegation specifier, this will break parsing
        // and the following block will not be attached as a body to the constructor. Therefore
        // we need to use a regular identifier.
        val argumentList = tokenBefore.getNonStrictParentOfType<KtValueArgumentList>() ?: return null
        if (argumentList.parent is KtConstructorDelegationCall) return EMPTY_SUFFIX
        // If there is = in the argument list after caret, then breaking parsing with just $ prevents K2 from resolving function call,
        // i.e. `f ($ = )` is resolved to variable assignment and left part `f ($` is resolved to erroneous name reference,
        // so we need to use `$,` to avoid resolving to variable assignment
        return "$DEFAULT_PARSING_BREAKER,"
    }

    private companion object {
        private const val DEFAULT_PARSING_BREAKER: String = "$" // add '$' to ignore context after the caret
        private const val EMPTY_SUFFIX: String = ""

        private val declarationKeywords: TokenSet = TokenSet.create(KtTokens.FUN_KEYWORD, KtTokens.VAL_KEYWORD, KtTokens.VAR_KEYWORD)
        private val typeArgumentsTokens: TokenSet = TokenSet.orSet(
            TokenSet.create(
                KtTokens.IDENTIFIER, KtTokens.LT, KtTokens.GT,
                KtTokens.COMMA, KtTokens.DOT, KtTokens.QUEST, KtTokens.COLON,
                KtTokens.IN_KEYWORD, KtTokens.OUT_KEYWORD,
                KtTokens.LPAR, KtTokens.RPAR, KtTokens.ARROW,
                TokenType.ERROR_ELEMENT
            ),
            KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET
        )
    }
}
