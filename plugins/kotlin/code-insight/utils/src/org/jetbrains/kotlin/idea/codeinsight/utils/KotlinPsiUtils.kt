// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.deleteBody
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.negate
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

fun KtContainerNode.getControlFlowElementDescription(): String? {
    when (node.elementType) {
        KtNodeTypes.THEN -> return "if"
        KtNodeTypes.ELSE -> return "else"
        KtNodeTypes.BODY -> {
            when (parent) {
                is KtWhileExpression -> return "while"
                is KtDoWhileExpression -> return "do...while"
                is KtForExpression -> return "for"
            }
        }
    }
    return null
}

/**
 * Returns whether the property accessor is a redundant getter or not.
 * TODO: We place this function in kotlin.code-insight.utils because it looks specific for redundant-getter-inspection.
 *       However, if we find some cases later that need this function for a general-purpose, we should move it to kotlin.base.psi.
 */
fun KtPropertyAccessor.isRedundantGetter(respectComments: Boolean = true): Boolean {
    if (!isGetter) return false
    if (respectComments && anyDescendantOfType<PsiComment>()) return false
    val expression = bodyExpression ?: return canBeCompletelyDeleted()
    if (expression.isBackingFieldReferenceTo(property)) return true
    if (expression is KtBlockExpression) {
        val statement = expression.statements.singleOrNull() ?: return false
        val returnExpression = statement as? KtReturnExpression ?: return false
        return returnExpression.returnedExpression?.isBackingFieldReferenceTo(property) == true
    }
    return false
}

fun KtExpression.isBackingFieldReferenceTo(property: KtProperty): Boolean =
    this is KtNameReferenceExpression
            && text == KtTokens.FIELD_KEYWORD.value
            && property.isAncestor(this)

fun KtPropertyAccessor.canBeCompletelyDeleted(): Boolean {
    if (modifierList == null) return true
    if (annotationEntries.isNotEmpty()) return false
    if (hasModifier(KtTokens.EXTERNAL_KEYWORD)) return false
    return visibilityModifierTypeOrDefault() == property.visibilityModifierTypeOrDefault()
}

fun removeRedundantGetter(getter: KtPropertyAccessor) {
    val property = getter.property
    val accessorTypeReference = getter.returnTypeReference
    if (accessorTypeReference != null && property.typeReference == null && property.initializer == null) {
        property.typeReference = accessorTypeReference
    }
    if (getter.canBeCompletelyDeleted()) {
        getter.delete()
    } else {
        getter.deleteBody()
    }
}

fun removeProperty(ktProperty: KtProperty) {
    val initializer = ktProperty.initializer
    if (initializer != null && initializer !is KtConstantExpression) {
        val commentSaver = CommentSaver(ktProperty)
        val replaced = ktProperty.replace(initializer)
        commentSaver.restore(replaced)
    } else {
        ktProperty.delete()
    }
}

fun renameToUnderscore(declaration: KtCallableDeclaration) {
    declaration.nameIdentifier?.replace(KtPsiFactory(declaration.project).createIdentifier("_"))
}


/**
 * A function that returns whether this KtParameter is a parameter of a setter or not.
 *
 * Since the parent of a KtParameter is KtParameterList and the parent of KtParameterList is the function or
 * the property accessor, this function checks whether `parent.parent` of KtParameter is a setter or not.
 */
val KtParameter.isSetterParameter: Boolean
    get() = (parent.parent as? KtPropertyAccessor)?.isSetter == true

fun KtPropertyAccessor.isRedundantSetter(respectComments: Boolean = true): Boolean {
    if (!isSetter) return false
    if (respectComments && anyDescendantOfType<PsiComment>()) return false
    val expression = bodyExpression ?: return canBeCompletelyDeleted()
    if (expression is KtBlockExpression) {
        val statement = expression.statements.singleOrNull() ?: return false
        val parameter = valueParameters.singleOrNull() ?: return false
        val binaryExpression = statement as? KtBinaryExpression ?: return false
        return binaryExpression.operationToken == KtTokens.EQ
                && binaryExpression.left?.isBackingFieldReferenceTo(property) == true
                && binaryExpression.right?.mainReference?.resolve() == parameter
    }
    return false
}

fun removeRedundantSetter(setter: KtPropertyAccessor) {
    if (setter.canBeCompletelyDeleted()) {
        setter.delete()
    } else {
        setter.deleteBody()
    }
}

fun KtExpression?.isTrueConstant(): Boolean = this != null && node?.elementType == KtNodeTypes.BOOLEAN_CONSTANT && text == "true"

fun KtExpression?.isFalseConstant(): Boolean = this != null && node?.elementType == KtNodeTypes.BOOLEAN_CONSTANT && text == "false"

/**
 * We use [optionalBooleanExpressionCheck] only when checking [KtPrefixExpression] with "!" operation e.g., "!(expr)" has a
 * baseExpression with a boolean type e.g., check whether "expr" in "!expr" has a boolean type or not.
 */
fun KtExpression.negate(reformat: Boolean = true, optionalBooleanExpressionCheck: (KtExpression) -> Boolean = { true }): KtExpression {
    val specialNegation = specialNegation(reformat, optionalBooleanExpressionCheck)
    if (specialNegation != null) return specialNegation
    return KtPsiFactory(project).createExpressionByPattern(pattern = "!$0", this, reformat = reformat)
}

private fun KtExpression.specialNegation(reformat: Boolean, optionalBooleanExpressionCheck: (KtExpression) -> Boolean): KtExpression? {
    val psiFactory = KtPsiFactory(project)
    when (this) {
        is KtPrefixExpression -> {
            if (operationReference.getReferencedName() == "!") {
                val baseExpression = baseExpression
                if (baseExpression != null) {
                    if (optionalBooleanExpressionCheck(baseExpression)) {
                        return KtPsiUtil.safeDeparenthesize(baseExpression)
                    }
                }
            }
        }

        is KtBinaryExpression -> {
            val operator = operationToken
            if (operator !in NEGATABLE_OPERATORS) return null
            val left = left ?: return null
            val right = right ?: return null
            return psiFactory.createExpressionByPattern(
                "$0 $1 $2", left, getNegatedOperatorText(operator), right,
                reformat = reformat
            )
        }

        is KtIsExpression -> {
            return psiFactory.createExpressionByPattern(
                "$0 $1 $2",
                leftHandSide,
                if (isNegated) "is" else "!is",
                typeReference ?: return null,
                reformat = reformat
            )
        }

        is KtConstantExpression -> {
            return when (text) {
                "true" -> psiFactory.createExpression("false")
                "false" -> psiFactory.createExpression("true")
                else -> null
            }
        }
    }
    return null
}

private val NEGATABLE_OPERATORS = setOf(
    KtTokens.EQEQ, KtTokens.EXCLEQ, KtTokens.EQEQEQ,
    KtTokens.EXCLEQEQEQ, KtTokens.IS_KEYWORD, KtTokens.NOT_IS, KtTokens.IN_KEYWORD,
    KtTokens.NOT_IN, KtTokens.LT, KtTokens.LTEQ, KtTokens.GT, KtTokens.GTEQ
)

private fun getNegatedOperatorText(token: IElementType): String {
    val negatedOperator = token.negate() ?: throw IllegalArgumentException("The token $token does not have a negated equivalent.")
    return negatedOperator.value
}

fun KtQualifiedExpression.getLeftMostReceiverExpression(): KtExpression =
    (receiverExpression as? KtQualifiedExpression)?.getLeftMostReceiverExpression() ?: receiverExpression

fun KtExpression.getLeftMostReceiverExpressionOrThis(): KtExpression =
    (this as? KtQualifiedExpression)?.getLeftMostReceiverExpression() ?: this

fun KtDotQualifiedExpression.replaceFirstReceiver(
    factory: KtPsiFactory,
    newReceiver: KtExpression,
    safeAccess: Boolean = false
): KtExpression {
    val replacedExpression = when {
        safeAccess -> this.replaced(factory.createExpressionByPattern("$0?.$1", receiverExpression, selectorExpression!!))
        else -> this
    } as KtQualifiedExpression

    when (val receiver = replacedExpression.receiverExpression) {
        is KtDotQualifiedExpression -> receiver.replace(receiver.replaceFirstReceiver(factory, newReceiver, safeAccess))
        else -> receiver.replace(newReceiver)
    }

    return replacedExpression
}

val KtQualifiedExpression.callExpression: KtCallExpression?
    get() = selectorExpression as? KtCallExpression

fun KtCallExpression.singleArgumentExpression(): KtExpression? {
    return valueArguments.singleOrNull()?.getArgumentExpression()
}

/**
 * Checks if there are any annotations in type or its type arguments.
 */
fun KtTypeReference.isAnnotatedDeep(): Boolean {
    return this.anyDescendantOfType<KtAnnotationEntry>()
}

private fun KtExpression.isIntegerConstantOfValue(value: Int): Boolean {
    val deparenthesized = KtPsiUtil.deparenthesize(this) as? KtConstantExpression
        ?: return false

    return deparenthesized.elementType == KtStubElementTypes.INTEGER_CONSTANT
            && deparenthesized.text.toIntOrNull() == value
}

val KtExpression.isZeroIntegerConstant: Boolean
    get() = isIntegerConstantOfValue(0)


val KtExpression.isOneIntegerConstant: Boolean
    get() = isIntegerConstantOfValue(1)

fun KtPsiFactory.appendSemicolonBeforeLambdaContainingElement(element: PsiElement) {
    val previousElement = KtPsiUtil.skipSiblingsBackwardByPredicate(element) {
        it!!.node.elementType in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET
    }
    if (previousElement != null && previousElement is KtExpression) {
        previousElement.parent.addAfter(createSemicolon(), previousElement)
    }
}

fun IElementType.invertedComparison(): KtSingleValueToken? = when (this) {
    KtTokens.LT -> KtTokens.GT
    KtTokens.GT -> KtTokens.LT
    KtTokens.GTEQ -> KtTokens.LTEQ
    KtTokens.LTEQ -> KtTokens.GTEQ
    else -> null
}

/**
 * Returns the first parent of `this` that is not a [KtParenthesizedExpression] and tries to cast it to [KtPrefixExpression].
 * Useful to unwrap statements like `!((...))` or `-((...))`.
 */
fun PsiElement.getWrappingPrefixExpressionOrNull(): KtPrefixExpression? =
    (getLastParentOfTypeInRow<KtParenthesizedExpression>() ?: this).parent as? KtPrefixExpression

fun createArgumentWithoutName(argument: KtValueArgument, isVararg: Boolean = false, isArrayOf: Boolean = false): List<KtValueArgument> {
    if (!argument.isNamed()) return listOf(argument.copied())
    val argumentExpr = argument.getArgumentExpression() ?: return emptyList()
    val psiFactory = KtPsiFactory(argument)
    return when {
        isVararg && argumentExpr is KtCollectionLiteralExpression ->
            argumentExpr.getInnerExpressions().map { psiFactory.createArgument(it) }

        isVararg && argumentExpr is KtCallExpression && isArrayOf ->
            argumentExpr.valueArguments.map { psiFactory.createArgument(it.getArgumentExpression()) }

        else -> listOf(psiFactory.createArgument(argumentExpr, name = null, isVararg))
    }
}

/**
 * A [KtDotQualifiedExpression] doesn't have an instance of class as a receiver if the selector is
 *   1. A class or an object or
 *   2. A constructor or
 *   3. A static method or
 *   4. A [KtCallableDeclaration] e.g., [KtNamedFunction] defined in an object when the declaration has a null receiverTypeReference.
 *
 * FYI, selector of [KtDotQualifiedExpression] is the right side of the dot operation e.g., `bar()` in `foo.bar()`.
 */
private fun KtDotQualifiedExpression.hasNoClassInstanceReceiver(): Boolean {
    val element = getQualifiedElementSelector()?.mainReference?.resolve() ?: return false
    return element is KtClassOrObject ||
            element is KtConstructor<*> ||
            element is KtCallableDeclaration && element.receiverTypeReference == null && (element.containingClassOrObject is KtObjectDeclaration?) ||
            element is PsiMember && element.hasModifier(JvmModifier.STATIC) ||
            element is PsiMethod && element.isConstructor
}

tailrec fun KtDotQualifiedExpression.expressionWithoutClassInstanceAsReceiver(): KtDotQualifiedExpression? =
    if (hasNoClassInstanceReceiver()) this
    else (receiverExpression as? KtDotQualifiedExpression)?.expressionWithoutClassInstanceAsReceiver()

fun KtClass.isOpen(): Boolean = hasModifier(KtTokens.OPEN_KEYWORD)
fun KtClass.isInheritable(): Boolean = isOpen() || isAbstract() || isSealed()

@ApiStatus.Internal
context(KaSession)
fun KtExpression.isSynthesizedFunction(): Boolean {
    val symbol =
        resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol ?: mainReference?.resolveToSymbol() ?: return false
    return symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED
}

@ApiStatus.Internal
context(KaSession)
fun KtCallExpression.isCallingAnyOf(vararg fqNames: FqName): Boolean {
    val calleeText = calleeExpression?.text ?: return false
    val targetFqNames = fqNames.filter { it.shortName().asString() == calleeText }
    if (targetFqNames.none()) return false

    val fqName = resolveToCall()
        ?.singleFunctionCallOrNull()
        ?.partiallyAppliedSymbol
        ?.symbol
        ?.callableId
        ?.asSingleFqName()
        ?: return false
    return targetFqNames.any { it == fqName }
}

@ApiStatus.Internal
operator fun FqName.plus(name: Name): FqName = child(name)

@ApiStatus.Internal
operator fun FqName.plus(name: String): FqName = this + Name.identifier(name)

private val KOTLIN_BUILTIN_ENUM_FUNCTION_FQ_NAMES = arrayOf(
    StandardKotlinNames.Enum.enumEntries,
    StandardKotlinNames.Enum.enumValues,
    StandardKotlinNames.Enum.enumValueOf
)

context(KaSession)
fun KtTypeReference.isReferenceToBuiltInEnumFunction(): Boolean {
    val target = (parent.getStrictParentOfType<KtTypeArgumentList>() ?: this)
        .getParentOfTypes(true, KtCallExpression::class.java, KtCallableDeclaration::class.java)
    return when (target) {
        is KtCallExpression -> target.isCallingAnyOf(*KOTLIN_BUILTIN_ENUM_FUNCTION_FQ_NAMES)
        is KtCallableDeclaration -> {
            target.anyDescendantOfType<KtCallExpression> {
                it.isCallingAnyOf(*KOTLIN_BUILTIN_ENUM_FUNCTION_FQ_NAMES) && it.isUsedAsExpression
            }
        }

        else -> false
    }
}

context(KaSession)
fun KtCallExpression.isReferenceToBuiltInEnumFunction(): Boolean {
    val calleeExpression = this.calleeExpression ?: return false
    return (calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName() in ENUM_STATIC_METHOD_NAMES && calleeExpression.isSynthesizedFunction()
}

context(KaSession)
fun KtCallableReferenceExpression.isReferenceToBuiltInEnumFunction(): Boolean {
    return this.canBeReferenceToBuiltInEnumFunction() && this.callableReference.isSynthesizedFunction()
}

val KtIfExpression.branches: List<KtExpression?>
    get() {
        fun KtExpression.ifBranchesOrThis(): List<KtExpression?> {
            if (this !is KtIfExpression) return listOf(this)
            return listOf(then) + `else`?.ifBranchesOrThis().orEmpty()
        }

        return ifBranchesOrThis()
    }

fun KtClass.isFunInterface(): Boolean = isInterface() && getFunKeyword() != null

fun KtParenthesizedExpression.removeUnnecessaryParentheses() {
    val commentSaver = CommentSaver(this)
    val innerExpression = this.expression ?: return
    val binaryExpressionParent = this.parent as? KtBinaryExpression
    val ktPsiFactory = KtPsiFactory(this.project)

    val replaced = if (binaryExpressionParent != null &&
        innerExpression is KtBinaryExpression &&
        binaryExpressionParent.right == this
    ) {
        val newElement = ktPsiFactory.createExpressionByPattern(
            "$0 $1 $2 $3 $4",
            binaryExpressionParent.left!!,
            binaryExpressionParent.operationReference,
            innerExpression.left!!,
            innerExpression.operationReference,
            innerExpression.right!!,
        )
        val replace = binaryExpressionParent.replace(newElement)
        replace.replace(ktPsiFactory.createExpression(replace.text))
    } else {
        this.replace(innerExpression)
    }

    if (innerExpression.firstChild is KtLambdaExpression) {
        ktPsiFactory.appendSemicolonBeforeLambdaContainingElement(replaced)
    }

    commentSaver.restore(replaced)
}