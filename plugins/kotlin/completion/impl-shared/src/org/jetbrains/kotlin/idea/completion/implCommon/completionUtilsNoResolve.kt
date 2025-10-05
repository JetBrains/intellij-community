// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfTypes
import com.intellij.ui.JBColor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render

@ApiStatus.Internal
val KOTLIN_CAST_REQUIRED_COLOR: JBColor = JBColor(0x4E4040, 0x969696)

tailrec fun <T : Any> LookupElement.putUserDataDeep(key: Key<T>, value: T?) {
    if (this is LookupElementDecorator<*>) {
        delegate.putUserDataDeep(key, value)
    } else {
        putUserData(key, value)
    }
}

tailrec fun <T : Any> LookupElement.getUserDataDeep(key: Key<T>): T? {
    return if (this is LookupElementDecorator<*>) {
        delegate.getUserDataDeep(key)
    } else {
        getUserData(key)
    }
}

fun createKeywordElement(
    keyword: String,
    tail: String = "",
    lookupObject: KeywordLookupObject = KeywordLookupObject()
): LookupElementBuilder {
    var element = LookupElementBuilder.create(lookupObject, keyword + tail)
    element = element.withPresentableText(keyword)
    element = element.withBoldness(true)
    if (tail.isNotEmpty()) {
        element = element.withTailText(tail, false)
    }
    return element
}

fun createKeywordElementWithSpace(
    keyword: String,
    tail: String = "",
    addSpaceAfter: Boolean = false,
    lookupObject: KeywordLookupObject = KeywordLookupObject()
): LookupElement {
    val element = createKeywordElement(keyword, tail, lookupObject)
    return if (addSpaceAfter) {
        element.withInsertHandler(WithTailInsertHandler.SPACE.asPostInsertHandler)
    } else {
        element
    }
}

fun Name?.labelNameToTail(): String = if (this != null) "@" + render() else ""

@Serializable
enum class ItemPriority {
    SUPER_METHOD_WITH_ARGUMENTS,
    FROM_UNRESOLVED_NAME_SUGGESTION,
    BRACKET_OPERATOR,
    DEFAULT,
    IMPLEMENT,
    OVERRIDE,
    STATIC_MEMBER_FROM_IMPORTS,
    STATIC_MEMBER
}

/**
 *  Checks whether user likely expects completion at the given position to offer a return statement.
 */
fun isLikelyInPositionForReturn(position: KtElement, parent: KtDeclarationWithBody, parentReturnUnit: Boolean): Boolean {
    val isInTopLevelInUnitFunction = parentReturnUnit && position.parent?.parent === parent

    val isInsideLambda = position.getNonStrictParentOfType<KtFunctionLiteral>()?.let { parent.isAncestor(it) } == true
    return when {
        isInsideLambda -> false // for now we do not want to alter completion inside lambda bodies
        position.inReturnExpression() -> false
        position.isRightOperandInElvis() -> true
        position.isLastOrSingleStatement() && !position.isDirectlyInLoopBody() && !isInTopLevelInUnitFunction -> true
        else -> false
    }
}

private fun KtElement.inReturnExpression(): Boolean = findReturnExpression(this) != null

fun KtElement.isRightOperandInElvis(): Boolean {
    val elvisParent = parent as? KtBinaryExpression ?: return false
    return elvisParent.operationToken == KtTokens.ELVIS && elvisParent.right === this
}

/**
 * Checks if expression is either last expression in a block, or a single expression in position where single
 * expressions are allowed (`when` entries, `for` and `while` loops, and `if`s).
 */
private fun PsiElement.isLastOrSingleStatement(): Boolean =
    when (val containingExpression = parent) {
        is KtBlockExpression -> containingExpression.statements.lastOrNull() === this
        is KtWhenEntry, is KtContainerNodeForControlStructureBody -> true
        else -> false
    }

private fun KtElement.isDirectlyInLoopBody(): Boolean {
    val loopContainer = when (val blockOrContainer = parent) {
        is KtBlockExpression -> blockOrContainer.parent as? KtContainerNodeForControlStructureBody
        is KtContainerNodeForControlStructureBody -> blockOrContainer
        else -> null
    }

    return loopContainer?.parent is KtLoopExpression
}

/**
 * If [expression] is directly relates to the return expression already, this return expression will be found.
 *
 * Examples:
 *
 * ```kotlin
 * return 10                                        // 10 is in return
 * return if (true) 10 else 20                      // 10 and 20 are in return
 * return 10 ?: 20                                  // 10 and 20 are in return
 * return when { true -> 10 ; else -> { 20; 30 } }  // 10 and 30 are in return, but 20 is not
 * ```
 */
private tailrec fun findReturnExpression(expression: PsiElement?): KtReturnExpression? =
    when (val parent = expression?.parent) {
        is KtReturnExpression -> parent
        is KtBinaryExpression -> if (parent.operationToken == KtTokens.ELVIS) findReturnExpression(parent) else null
        is KtContainerNodeForControlStructureBody, is KtIfExpression -> findReturnExpression(parent)
        is KtBlockExpression -> if (expression.isLastOrSingleStatement()) findReturnExpression(parent) else null
        is KtWhenEntry -> findReturnExpression(parent.parent)
        else -> null
    }

var LookupElement.priority by UserDataProperty(Key<ItemPriority>("ITEM_PRIORITY_KEY"))
fun LookupElement.suppressAutoInsertion() = AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(this)

fun referenceScope(declaration: KtNamedDeclaration): KtElement? = when (val parent = declaration.parent) {
    is KtParameterList -> parent.parent as KtElement
    is KtClassBody -> {
        val classOrObject = parent.parent as KtClassOrObject
        if (classOrObject is KtObjectDeclaration && classOrObject.isCompanion()) {
            classOrObject.containingClassOrObject
        } else {
            classOrObject
        }
    }

    is KtFile -> parent
    is KtBlockExpression -> parent
    else -> null
}

fun findValueArgument(expression: KtExpression): KtValueArgument? {
    // Search for value argument among parents to avoid parsing errors like KTIJ-18231 and KTIJ-30471
    // The parsing errors in value argument lists in the relevant positions always seem to be a binary
    // operation with an identifier as operation.
    return expression.parents
        .dropWhile { it is KtBinaryExpression && it.operationToken == KtTokens.IDENTIFIER }
        .firstOrNull() as? KtValueArgument
}

infix fun <T> ElementPattern<T>.and(rhs: ElementPattern<T>) = StandardPatterns.and(this, rhs)
fun <T> ElementPattern<T>.andNot(rhs: ElementPattern<T>) = StandardPatterns.and(this, StandardPatterns.not(rhs))
infix fun <T> ElementPattern<T>.or(rhs: ElementPattern<T>) = StandardPatterns.or(this, rhs)

fun singleCharPattern(char: Char) = StandardPatterns.character().equalTo(char)

fun kotlinIdentifierStartPattern(): ElementPattern<Char> =
    StandardPatterns.character().javaIdentifierStart().andNot(singleCharPattern('$'))

fun kotlinIdentifierPartPattern(): ElementPattern<Char> =
    StandardPatterns.character().javaIdentifierPart().andNot(singleCharPattern('$')) or singleCharPattern('@')


fun LookupElementPresentation.prependTailText(text: String, grayed: Boolean) {
    val tails = tailFragments.toList()
    clearTail()
    appendTailText(text, grayed)
    tails.forEach { appendTailText(it.text, it.isGrayed) }
}

typealias NameFilter = (Name) -> Boolean

fun PrefixMatcher.asNameFilter(): NameFilter {
    return { name -> !name.isSpecial && prefixMatches(name.identifier) }
}

infix fun <T> ((T) -> Boolean).exclude(otherFilter: (T) -> Boolean): (T) -> Boolean = { this(it) && !otherFilter(it) }

/**
 * Returns true when [position] is an identifier of a function argument or a part of a binary expression (left- or right- hand side).
 *
 * Here only the positions in which expected type is likely to be unresolved are taken into consideration, in other positions suitability
 * depends on the calculated expected type.
 */
fun isPositionSuitableForNull(position: PsiElement): Boolean = when {
    position.elementType != KtTokens.IDENTIFIER -> false
    position.context?.parent is KtValueArgument -> true
    else -> position.context?.getPrevSiblingIgnoringWhitespaceAndComments() is KtOperationReferenceExpression
            || position.context?.getNextSiblingIgnoringWhitespaceAndComments() is KtOperationReferenceExpression
}

fun isPositionInsideImportOrPackageDirective(position: PsiElement): Boolean =
    position.parentOfTypes(KtImportDirective::class, KtPackageDirective::class) != null

fun KtElement.reference() = when (this) {
    is KtCallExpression -> calleeExpression?.mainReference
    is KtDotQualifiedExpression -> selectorExpression?.mainReference
    else -> mainReference
}

val KDocLink.qualifier: List<String> get() = getLinkText().split('.').dropLast(1)

fun isAtFunctionLiteralStart(position: PsiElement): Boolean {
    val lBrace = PsiTreeUtil.prevCodeLeaf(position)
        ?.let { if (it.node.elementType == KtTokens.LPAR) PsiTreeUtil.prevCodeLeaf(it) else it }
        ?.takeIf { it.node.elementType == KtTokens.LBRACE }

    val functionLiteral = lBrace?.parent as? KtFunctionLiteral ?: return false
    return functionLiteral.lBrace == lBrace
}
