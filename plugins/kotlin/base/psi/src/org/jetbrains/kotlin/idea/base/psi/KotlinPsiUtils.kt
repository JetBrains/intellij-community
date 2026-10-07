// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinPsiUtils")
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
import com.intellij.util.asSafely
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.kotlin.idea.base.psi.extensions.ImplementationDetailClassNameCheckerProvider
import org.jetbrains.kotlin.lang.BinaryOperationPrecedence
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtDoubleColonExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtEnumEntrySuperclassReferenceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStatementExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelInFileOrScript
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.unpackFunctionLiteral
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration as isExpectDeclaration_alias

val KtClassOrObject.classIdIfNonLocal: ClassId?
    get() {
        if (KtPsiUtil.isLocal(this)) return null
        val packageName = containingKtFile.packageFqName
        val classesNames = parentsOfType<KtDeclaration>().filterNot { it is KtScript }.map { it.name }.toList().asReversed()
        if (classesNames.any { it == null }) return null
        return ClassId(packageName, FqName(classesNames.joinToString(separator = ".")), /*local=*/false)
    }

/**
 * Same as [classIdIfNonLocal] but omits implementation detail classes (such as Line_X_jupyter in notebooks)
 * in names.
 *
 * It should be legitimate to omit implementation detail class names in all contexts
 * where we generate a user-visible ClassId.
 * However, this property shouldn't be used to get a unique identifier of the class
 * (something that will be used as an argument of `equals`).
 * So, it should be safe to change the usage of [classIdIfNonLocal] to the usage of this property,
 * but be cautious not to use it to generate a unique identifier.
 */
val KtClassOrObject.presentableClassId: ClassId?
    get() {
        if (KtPsiUtil.isLocal(this)) return null
        val packageName = containingKtFile.packageFqName
        val checker = ImplementationDetailClassNameCheckerProvider.get(this)
        val classNames = buildList {
            for (clazz in parentsOfType<KtDeclaration>()) {
                val className = clazz.name
                when {
                    className == null -> return null
                    checker.isImplementationDetail(className) -> continue
                    else -> add(className)
                }
            }
        }
        return ClassId(
            packageFqName = packageName,
            relativeClassName = FqName(classNames.asReversed().joinToString(separator = ".")),
            isLocal = false
        )
    }

val KtCallableDeclaration.callableIdIfNotLocal: CallableId?
    get() {
        val callableName = this.nameAsName ?: return null
        if (isTopLevelInFileOrScript(this)) {
            return CallableId(containingKtFile.packageFqName, callableName)
        }

        val classId = containingClassOrObject?.classIdIfNonLocal ?: return null
        return CallableId(classId, callableName)
    }

fun getElementAtOffsetIgnoreWhitespaceBefore(file: PsiFile, offset: Int): PsiElement? {
    val element = file.findElementAt(offset)
    return if (element is PsiWhiteSpace) {
        file.findElementAt(element.getTextRange().endOffset)
    } else {
        element
    }
}

fun getElementAtOffsetIgnoreWhitespaceAfter(file: PsiFile, offset: Int): PsiElement? {
    val element = file.findElementAt(offset - 1)
    return if (element is PsiWhiteSpace) {
        file.findElementAt(element.getTextRange().startOffset - 1)
    } else {
        element
    }
}

fun getStartLineOffset(file: PsiFile, line: Int): Int? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    if (line >= document.lineCount) {
        return null
    }

    val lineStartOffset = document.getLineStartOffset(line)
    return CharArrayUtil.shiftForward(document.charsSequence, lineStartOffset, " \t")
}

fun getEndLineOffset(file: PsiFile, line: Int): Int? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    if (line >= document.lineCount) {
        return null
    }

    val lineStartOffset = document.getLineEndOffset(line)
    return CharArrayUtil.shiftBackward(document.charsSequence, lineStartOffset, " \t")
}

fun getTopmostElementAtOffset(element: PsiElement, offset: Int): PsiElement {
    var node = element
    do {
        val parent = node.parent
        if (parent == null || !parent.isSuitableTopmostElementAtOffset(offset)) {
            break
        }
        node = parent
    } while (true)

    return node
}

fun getTopParentWithEndOffset(element: PsiElement, stopAt: Class<*>): PsiElement {
    var node = element
    val endOffset = node.textOffset + node.textLength
    do {
        val parent = node.parent ?: break
        if (parent.textOffset + parent.textLength != endOffset) {
            break
        }

        node = parent
        if (stopAt.isInstance(node)) {
            break
        }
    } while (true)

    return node
}

@SafeVarargs
@Suppress("UNCHECKED_CAST")
fun <T> getTopmostElementAtOffset(element: PsiElement, offset: Int, vararg classes: Class<out T>): T? {
    var node = element
    var lastElementOfType: T? = null
    if (classes.anyIsInstance(node)) {
        lastElementOfType = node as? T
    }

    do {
        val parent = node.parent
        if (parent == null || !parent.isSuitableTopmostElementAtOffset(offset)) {
            break
        }
        if (classes.anyIsInstance(parent)) {
            lastElementOfType = parent as? T
        }
        node = parent
    } while (true)

    return lastElementOfType
}

/**
 * @return the [FqName] of the first non-local declaration containing [offset]
 */
fun getFqNameAtOffset(file: KtFile, offset: Int): FqName? {
    if (offset !in 0 until file.textLength) return null

    return file.findElementAt(offset)?.parents(withSelf = true)?.mapNotNull { it.kotlinFqName }?.firstOrNull()
}

private fun <T> Array<out Class<out T>>.anyIsInstance(element: PsiElement): Boolean =
    any { it.isInstance(element) }

private fun PsiElement.isSuitableTopmostElementAtOffset(offset: Int): Boolean =
    textOffset >= offset && this !is KtBlockExpression && this !is PsiFile


fun KtExpression.safeDeparenthesize(): KtExpression = KtPsiUtil.safeDeparenthesize(this)

fun KtDeclaration.isEffectivelyActual(checkConstructor: Boolean = true): Boolean = when {
    hasActualModifier() -> true
    this is KtEnumEntry || checkConstructor && this is KtConstructor<*> -> containingClass()?.hasActualModifier() == true
    else -> false
}

fun KtPropertyAccessor.deleteBody() {
    deleteChildRange(parameterList ?: return, lastChild)
}

/**
 * Does one of two conversions:
 * * `fun foo() = value` -> `value`
 * * `fun foo() { return value }` -> `value`
 */
fun KtDeclarationWithBody.singleExpressionBody(): KtExpression? =
    when (val body = bodyExpression) {
        is KtBlockExpression -> body.statements.singleOrNull()?.asSafely<KtReturnExpression>()?.returnedExpression
        else -> body
    }

fun KtNamedDeclaration.isConstructorDeclaredProperty(): Boolean =
    this is KtParameter && ownerFunction is KtPrimaryConstructor && hasValOrVar()

fun KtExpression.getCallChain(): List<KtExpression> =
    generateSequence(this) { (it as? KtDotQualifiedExpression)?.receiverExpression }
        .map { (it as? KtDotQualifiedExpression)?.selectorExpression ?: it }
        .toList()
        .reversed()

fun KtCallExpression.getContainingValueArgument(expression: KtExpression): KtValueArgument? {
    fun KtElement.deparenthesizeStructurally(): KtElement? {
        val deparenthesized = if (this is KtExpression) KtPsiUtil.deparenthesizeOnce(this) else this
        return when {
            deparenthesized != this -> deparenthesized
            this is KtLambdaExpression -> this.functionLiteral
            this is KtFunctionLiteral -> this.bodyExpression
            else -> null
        }
    }

    for (valueArgument in valueArguments) {
        val argumentExpression = valueArgument.getArgumentExpression() ?: continue
        val candidates = generateSequence<KtElement>(argumentExpression) { it.deparenthesizeStructurally() }
        if (expression in candidates) {
            return valueArgument
        }
    }

    return null
}

val KtCallExpression.samConstructorValueArgument: KtValueArgument?
    get() = valueArguments.singleOrNull()?.takeIf { it.getArgumentExpression() is KtLambdaExpression }

fun KtClass.mustHaveNonEmptyPrimaryConstructor(): Boolean =
    isData() || isInlineOrValue()

fun KtClass.mustHaveOnlyPropertiesInPrimaryConstructor(): Boolean =
    isData() || isAnnotation() || isInlineOrValue()

fun KtClass.mustHaveOnlyValPropertiesInPrimaryConstructor(): Boolean =
    isAnnotation() || isInlineOrValue()

fun KtClass.isInlineOrValue(): Boolean =
    isInline() || isValue()

fun KtModifierListOwner.hasInlineModifier(): Boolean =
    hasModifier(KtTokens.INLINE_KEYWORD)

fun KtPrimaryConstructor.mustHaveValOrVar(): Boolean =
    containingClass()?.mustHaveOnlyPropertiesInPrimaryConstructor() ?: false

fun KtNamedDeclaration.isAlwaysActual(): Boolean = safeAs<KtParameter>()?.parent?.parent?.safeAs<KtPrimaryConstructor>()
    ?.mustHaveValOrVar() ?: false

fun KtPrimaryConstructor.isRedundant(): Boolean {
    val containingClass = containingClass() ?: return false
    return when {
        valueParameters.isNotEmpty() -> false
        annotations.isNotEmpty() -> false
        modifierList?.text?.isBlank() == false -> false
        isExpectDeclaration_alias() -> false
        containingClass.mustHaveNonEmptyPrimaryConstructor() -> false
        containingClass.secondaryConstructors.isNotEmpty() -> false
        else -> true
    }
}

fun PsiElement.childrenDfsSequence(): Sequence<PsiElement> =
    sequence {
        suspend fun SequenceScope<PsiElement>.visit(element: PsiElement) {
            element.children.forEach { visit(it) }
            yield(element)
        }
        visit(this@childrenDfsSequence)
    }

fun ValueArgument.findSingleLiteralStringTemplateText(): String? {
    return getArgumentExpression()
        ?.safeAs<KtStringTemplateExpression>()
        ?.entries
        ?.singleOrNull()
        ?.safeAs<KtLiteralStringTemplateEntry>()
        ?.text
}

fun PsiElement.isInsideAnnotationEntryArgumentList(): Boolean = parentOfType<KtValueArgumentList>()?.parent is KtAnnotationEntry

fun KtExpression.unwrapIfLabeled(): KtExpression {
    var statement = this
    while (true) {
        statement = statement.parent as? KtLabeledExpression ?: return statement
    }
}

fun KtExpression.previousStatement(): KtExpression? {
    val statement = unwrapIfLabeled()
    if (statement.parent !is KtBlockExpression) return null
    return statement.siblings(forward = false, withItself = false).firstIsInstanceOrNull()
}

fun getCallElement(argument: KtValueArgument): KtCallElement? {
    return if (argument is KtLambdaArgument) {
        argument.parent as? KtCallElement
    } else {
        argument.parents.match(KtValueArgumentList::class, last = KtCallElement::class)
    }
}

val PsiElement.isInsideKtTypeReference: Boolean
    get() = getNonStrictParentOfType<KtTypeReference>() != null

/**
 * Returns the name of the label which can be used to perform the labeled return
 * from the current lambda, if the lambda is present and if the labeled return is possible.
 *
 * The name corresponds either to:
 * - lambda's explicit label (`foo@{ ... }`)
 * - the name of the outer function call (`foo { ... }`)
 */
fun KtBlockExpression.getParentLambdaLabelName(): String? {
    val lambdaExpression = getStrictParentOfType<KtLambdaExpression>() ?: return null
    val callExpression = lambdaExpression.getStrictParentOfType<KtCallExpression>() ?: return null
    val valueArgument = callExpression.valueArguments.find {
        it.getArgumentExpression()?.unpackFunctionLiteral(allowParentheses = false) === lambdaExpression
    } ?: return null
    val lambdaLabelName = (valueArgument.getArgumentExpression() as? KtLabeledExpression)?.getLabelName()
    return lambdaLabelName ?: callExpression.getCallNameExpression()?.text
}

/**
 * Searches for a parameter with the given [name] in the parent function of the element.
 * If not found in the immediate parent function, it recursively searches in the enclosing parent functions.
 *
 * @param name The name of the parameter to search for.
 * @return The found `KtParameter` with the given name, or `null` if no parameter with such [name] is found.
 */
fun KtElement.findParameterWithName(name: String): KtParameter? {
    val function = getStrictParentOfType<KtFunction>() ?: return null
    return function.valueParameters.firstOrNull { it.name == name } ?: function.findParameterWithName(name)
}

fun KtSimpleNameExpression.isPartOfQualifiedExpression(): Boolean {
    var parent = parent
    while (parent is KtDotQualifiedExpression) {
        if (parent.selectorExpression !== this) return true
        parent = parent.parent
    }
    return false
}

fun KtTypeReference?.typeArguments(): List<KtTypeProjection> {
    return (this?.typeElement as? KtUserType)?.typeArguments.orEmpty()
}

fun KtNamedDeclaration.getReturnTypeReference(): KtTypeReference? = getReturnTypeReferences().singleOrNull()

fun KtNamedDeclaration.getReturnTypeReferences(): List<KtTypeReference> {
    return when (this) {
        is KtCallableDeclaration -> listOfNotNull(typeReference)
        is KtClassOrObject -> superTypeListEntries.mapNotNull { it.typeReference }
        is KtScript -> emptyList()
        else -> throw AssertionError("Unexpected declaration kind: $text")
    }
}

fun KtSimpleNameExpression.canBeUsedInImport(): Boolean {
    if (this is KtEnumEntrySuperclassReferenceExpression) return false
    if (parent is KtThisExpression || parent is KtSuperExpression) return false

    return true
}

/**
 * Checks if this element is on the left-hand side of an assignment expression.
 * Traverses parent hierarchy to handle cases like `(arr[i]) = value`.
 */
fun PsiElement.isAssignmentLHS(): Boolean = parents(withSelf = false).any {
    KtPsiUtil.isAssignment(it) && (it as KtBinaryExpression).left == this
}

fun KtDestructuringDeclarationEntry.isNameBased(): Boolean = ownValOrVarKeyword != null &&
        (parent as? KtDestructuringDeclaration)?.hasSquareBrackets() == false

/**
 * Returns `true` if the parentheses in `expression` are redundant and could be removed.
 *
 * Copied from KtPsiUtil.
 */
fun areParenthesesUseless(expression: KtParenthesizedExpression): Boolean {
    val innerExpression = expression.getExpression()
    if (innerExpression == null) return true
    val parent = expression.getParent()
    if (parent !is KtElement) return true
    return !areParenthesesNecessary(innerExpression, expression, parent)
}

/**
 * Returns `true` if parentheses around `innerExpression` are required for the code to keep its meaning, given that they
 * currently appear as `currentInner` inside `parentElement`. Accounts for operator precedence and the many syntactic
 * special cases where parentheses cannot be dropped.
 *
 * Copied from KtPsiUtil.
 */
fun areParenthesesNecessary(
    innerExpression: KtExpression,
    currentInner: KtExpression,
    parentElement: KtElement
): Boolean {
    if (parentElement is KtDelegatedSuperTypeEntry) return true

    if (parentElement is KtParenthesizedExpression || innerExpression is KtParenthesizedExpression) {
        return false
    }

    if (parentElement is KtPackageDirective) return false

    if (parentElement is KtWhenExpression || innerExpression is KtWhenExpression) {
        return false
    }

    if (parentElement is KtCollectionLiteralExpression) return false

    if (innerExpression is KtIfExpression) {
        if (parentElement is KtQualifiedExpression) return true

        var current: PsiElement = parentElement

        while (!(current is KtBlockExpression || current is KtDeclaration || current is KtStatementExpression || current is KtFile)) {
            if (current.getTextRange().endOffset != currentInner.getTextRange().endOffset) {
                return current !is KtParenthesizedExpression && current !is KtValueArgumentList // if current expression is "guarded" by parenthesis, no extra parenthesis is necessary
            }

            current = current.getParent()
        }
    }

    // a lambda prefixed with a label and/or annotations (`l@{}`, `@Ann {}`) is a valid trailing lambda as well,
    // so `foo()\n(l@{})` would turn into the call `foo() l@{}` without the parentheses
    fun KtExpression.unwrapLabelsAndAnnotations(): KtExpression? = when (this) {
        is KtLabeledExpression -> baseExpression?.unwrapLabelsAndAnnotations()
        is KtAnnotatedExpression -> baseExpression?.unwrapLabelsAndAnnotations()
        else -> this
    }

    if (innerExpression.unwrapLabelsAndAnnotations() is KtLambdaExpression) {
        val prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(currentInner)
        if (endWithParenthesisOrCallExpression(prevSibling)) return true
    }

    if (parentElement is KtCallExpression && currentInner === parentElement.getCalleeExpression()) {
        var targetInnerExpression: KtExpression? = innerExpression
        if (targetInnerExpression is KtDotQualifiedExpression) {
            val selector = targetInnerExpression.selectorExpression
            if (selector != null) {
                targetInnerExpression = selector
            }
        }
        if (targetInnerExpression is KtSimpleNameExpression) return false
        if (parentElement.getQualifiedExpressionForSelector() != null) return true
        if (targetInnerExpression is KtCallExpression && parentElement.getValueArgumentList() == null) return true
        return !(targetInnerExpression is KtThisExpression
                || targetInnerExpression is KtArrayAccessExpression
                || targetInnerExpression is KtConstantExpression
                || targetInnerExpression is KtStringTemplateExpression
                || targetInnerExpression is KtCallExpression)
    }

    if (parentElement is KtValueArgument) {
        // a(___, d > (e + f)) => a((b < c), d > (e + f)) to prevent parsing < c, d > as type argument list
        val nextArg = PsiTreeUtil.getNextSiblingOfType<KtValueArgument?>(parentElement, KtValueArgument::class.java)
        if (innerExpression is KtBinaryExpression && innerExpression.getOperationToken() === KtTokens.LT &&
            (nextArg?.getArgumentExpression() as? KtBinaryExpression)?.getOperationToken() === KtTokens.GT
        ) return true
    }

    val innerOperation = getOperation(innerExpression)

    if (innerExpression is KtBinaryExpression) {
        // '(x operator return [...]) operator ...' case
        if (parentElement is KtBinaryExpression && innerExpression.getRight() is KtReturnExpression) {
            return true
        }
        // '(x operator y)' case
        if (innerOperation !== KtTokens.ELVIS && (parentElement !is KtValueArgument) && (parentElement !is KtParameter) && (parentElement !is KtBlockStringTemplateEntry) && !(parentElement is KtContainerNode && parentElement !is KtContainerNodeForControlStructureBody) &&
            isKeepBinaryExpressionParenthesized(innerExpression)
        ) {
            return true
        }
    }

    if (parentElement !is KtExpression) return false

    val parentOperation = getOperation(parentElement)

    // 'return (@label{...})' case
    if (parentElement is KtReturnExpression
        && (innerExpression is KtLabeledExpression || innerExpression is KtAnnotatedExpression)
    ) return true

    // '(x: Int) < y' case
    if (innerExpression is KtBinaryExpressionWithTypeRHS && parentOperation === KtTokens.LT) {
        return true
    }

    if (parentElement is KtLabeledExpression) return false

    // 'x ?: ...' case
    if (parentElement is KtBinaryExpression && parentOperation === KtTokens.ELVIS && (innerExpression !is KtBinaryExpression) && currentInner === parentElement.getRight()) {
        return false
    }

    // 'x = fun {}' case
    if (parentElement is KtBinaryExpression && parentOperation === KtTokens.EQ &&
        innerExpression is KtNamedFunction && currentInner === parentElement.getRight()
    ) {
        return false
    }

    val innerPriority = getPriority(innerExpression)
    val parentPriority = getPriority(parentElement)

    if (innerPriority == parentPriority) {
        if (parentElement is KtBinaryExpression) {
            if (innerOperation === KtTokens.ANDAND || innerOperation === KtTokens.OROR) {
                return false
            }
            return parentElement.getRight() === currentInner
        }

        if (parentElement is KtPrefixExpression && innerExpression is KtPrefixExpression) {
            // +(++x) or +(+x) case
            if (parentOperation === KtTokens.PLUS) {
                return innerOperation === KtTokens.PLUS || innerOperation === KtTokens.PLUSPLUS
            }

            // -(--x) or -(-x) case
            if (parentOperation === KtTokens.MINUS) {
                return innerOperation === KtTokens.MINUS || innerOperation === KtTokens.MINUSMINUS
            }
        }
        return false
    }

    return innerPriority < parentPriority
}

private fun endWithParenthesisOrCallExpression(element: PsiElement?): Boolean {
    if (element == null) return false
    if (element.getText().endsWith(KtTokens.RPAR.value) || element is KtCallExpression) return true
    val children = element.getChildren()
    val length = children.size
    if (length == 0) return false
    return endWithParenthesisOrCallExpression(children[length - 1])
}

private fun isKeepBinaryExpressionParenthesized(expression: KtBinaryExpression): Boolean {
    var expr = expression.firstChild
    while (expr != null) {
        if (expr is PsiWhiteSpace && expr.textContains('\n')) {
            return true
        }
        if (expr is KtOperationReferenceExpression) {
            break
        }
        expr = expr.getNextSibling()
    }
    return (expression.getRight() is KtBinaryExpression && isKeepBinaryExpressionParenthesized((expression.getRight() as KtBinaryExpression?)!!)) ||
            (expression.getLeft() is KtBinaryExpression && isKeepBinaryExpressionParenthesized((expression.getLeft() as KtBinaryExpression?)!!))

}

private fun getOperation(expression: KtExpression): IElementType? {
    if (expression is KtQualifiedExpression) {
        return expression.operationSign
    } else if (expression is KtOperationExpression) {
        return expression.operationReference.getReferencedNameElementType()
    }
    return null
}

/**
 * The list of all available priorities:
 * 0 – for declaration and statements
 * 1..12 -- for enum values of binaries
 * 13 -- postfix
 * 14 -- prefix
 * 15 -- super and other
 */
val MAX_PRIORITY: Int = BinaryOperationPrecedence.entries.count() + 3

/**
 * @return priority (that opposed to precedence) of the passed <tt>expression</tt>
 */
private fun getPriority(expression: KtExpression): Int {
    if (expression is KtSuperExpression) {
        return MAX_PRIORITY
    }

    if (expression is KtPostfixExpression ||
        expression is KtQualifiedExpression ||
        expression is KtCallExpression ||
        expression is KtArrayAccessExpression ||
        expression is KtDoubleColonExpression
    ) {
        return MAX_PRIORITY - 1
    }

    if (expression is KtPrefixExpression || expression is KtLabeledExpression || expression is KtIfExpression) {
        return MAX_PRIORITY - 2
    }

    val operation = getOperation(expression)
    if (operation is KtToken) {
        val binaryPrecedence = BinaryOperationPrecedence.TOKEN_TO_BINARY_PRECEDENCE_MAP[operation]
        if (binaryPrecedence != null) {
            return (MAX_PRIORITY - 3) - binaryPrecedence.ordinal
        }
    }

    if (expression is KtDeclaration || expression is KtStatementExpression) {
        return 0
    }

    return MAX_PRIORITY
}

fun KtModifierList.addContextParameterList(newContextClause: KtContextParameterList): KtContextParameterList? {
    // insert before the first modifier keyword
    val insertPoint = getModifier(KtTokens.MODIFIER_KEYWORDS)
    return if (insertPoint != null) {
        addBefore(newContextClause, insertPoint)
    } else {
        add(newContextClause)
    } as? KtContextParameterList
}
