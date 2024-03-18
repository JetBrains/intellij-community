// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.collections.any
import kotlin.collections.singleOrNull
import kotlin.let
import kotlin.sequences.first

data class FoldInitializerAndIfExpressionData(
    val initializer: KtExpression,
    val variableDeclaration: KtVariableDeclaration,
    val ifNullExpression: KtExpression,
    val typeChecked: KtTypeReference? = null,
    val variableTypeString: String?
)

context(KtAnalysisSession)
fun prepareData(element: KtIfExpression): FoldInitializerAndIfExpressionData? {
    if (element.`else` != null) return null

    val operationExpression = element.condition as? KtOperationExpression ?: return null
    val value = when (operationExpression) {
        is KtBinaryExpression -> {
            if (operationExpression.operationToken != KtTokens.EQEQ) return null
            operationExpression.expressionComparedToNull()
        }

        is KtIsExpression -> {
            if (!operationExpression.isNegated) return null
            if (operationExpression.typeReference?.typeElement is KtNullableType) return null
            operationExpression.leftHandSide
        }

        else -> return null
    } as? KtNameReferenceExpression ?: return null

    if (element.parent !is KtBlockExpression) return null

    val variableDeclaration = element.siblings(forward = false, withItself = false)
        .firstIsInstanceOrNull<KtExpression>() as? KtVariableDeclaration ?: return null

    if (variableDeclaration.nameAsName != value.getReferencedNameAsName()) return null
    val initializer = variableDeclaration.initializer ?: return null
    val then = element.then ?: return null
    val statement = (if (then is KtBlockExpression) then.statements.singleOrNull() else then) ?: return null

    val typeReference = (operationExpression as? KtIsExpression)?.typeReference

    if (typeReference != null) {
        val checkedType = typeReference.getKtType()
        val variableType = variableDeclaration.getReturnKtType()
        if (!checkedType.isPossiblySubTypeOf(variableType)) return null
    }


    if (statement.getKtType()?.isNothing != true) return null

    if (ReferencesSearch.search(variableDeclaration, LocalSearchScope(statement)).findFirst() != null) {
        return null
    }

    val type = calculateType(variableDeclaration, element, initializer)?.let { type ->
        if (type is KtErrorType) null
        else type.render(position = Variance.OUT_VARIANCE)
    }

    return FoldInitializerAndIfExpressionData(
      initializer,
      variableDeclaration,
      statement,
      typeReference,
      type
    )
}

fun joinLines(element: KtIfExpression,
              variableDeclaration: KtVariableDeclaration,
              initializer: KtExpression,
              ifNullExpr: KtExpression,
              typeChecked: KtTypeReference?,
              variableTypeString: String?): KtBinaryExpression {
    val childRangeBefore = PsiChildRange(variableDeclaration, element)
    val commentSaver = CommentSaver(childRangeBefore)
    val childRangeAfter = childRangeBefore.withoutLastStatement()

    val factory = KtPsiFactory(element.project)
    val elvis = createElvisExpression(initializer, ifNullExpr, typeChecked, factory)

    val positionedElvis = initializer.replaced(elvis)

    element.delete()

    if (variableTypeString != null) {
        val typeReference = factory.createType(variableTypeString)
        variableDeclaration.setTypeReference(typeReference)?.let { shortenReferences(it) }
    }

    commentSaver.restore(childRangeAfter)

    variableDeclaration.reformat()
    return positionedElvis
}

context(KtAnalysisSession)
private fun calculateType(
    declaration: KtVariableDeclaration,
    element: KtIfExpression,
    initializer: KtExpression
) = when {
    // for var with no explicit type, add it so that the actual change won't change
    declaration.isVar && declaration.typeReference == null -> {
        if (element.condition is KtBinaryExpression) {
            val ifEndOffset = element.endOffset

            val isUsedAsNotNullable = ReferencesSearch.search(declaration, LocalSearchScope(declaration.parent)).any {
                if (it.element.startOffset <= ifEndOffset) return@any false
                !(it.element.safeAs<KtExpression>()?.getKtType()?.isMarkedNullable ?: return@any false)
            }

            if (isUsedAsNotNullable) null else initializer.getKtType()

        } else {
            initializer.getKtType()
        }
    }

    // for val with explicit type, change it to non-nullable
    !declaration.isVar && declaration.typeReference != null ->
        initializer.getKtType()?.withNullability(KtTypeNullability.NON_NULLABLE)

    else -> null
}

private fun KtExpression?.isNullExpression(): Boolean = this?.unwrapBlockOrParenthesis()?.node?.elementType == KtNodeTypes.NULL


private fun KtExpression.unwrapBlockOrParenthesis(): KtExpression {
    val innerExpression = KtPsiUtil.safeDeparenthesize(this, true)
    if (innerExpression is KtBlockExpression) {
        val statement = innerExpression.statements.singleOrNull() ?: return this
        val deparenthesized = KtPsiUtil.safeDeparenthesize(statement, true)
        if (deparenthesized is KtLambdaExpression) return this
        return deparenthesized
    }
    return innerExpression
}

private fun KtBinaryExpression.expressionComparedToNull(): KtExpression? {
    val operationToken = this.operationToken
    if (operationToken != KtTokens.EQEQ && operationToken != KtTokens.EXCLEQ) return null

    val right = this.right ?: return null
    val left = this.left ?: return null

    val rightIsNull = right.isNullExpression()
    val leftIsNull = left.isNullExpression()
    if (leftIsNull == rightIsNull) return null
    return if (leftIsNull) right else left
}

private fun createElvisExpression(
    initializer: KtExpression,
    ifNullExpr: KtExpression,
    typeReference: KtTypeReference?,
    factory: KtPsiFactory
): KtBinaryExpression {
    val pattern = "$0 ?: $1"
    val newInitializer = if (initializer is KtBinaryExpression &&
        initializer.operationToken == KtTokens.ELVIS &&
        initializer.right?.text == ifNullExpr.text
    ) {
        initializer.left ?: initializer
    } else {
        initializer
    }
    val elvis = factory.createExpressionByPattern(pattern, newInitializer, ifNullExpr) as KtBinaryExpression
    if (typeReference != null) {
        elvis.left!!.replace(factory.createExpressionByPattern("$0 as? $1", newInitializer, typeReference))
    }

    return elvis
}

private fun PsiChildRange.withoutLastStatement(): PsiChildRange {
    val newLast = last!!.siblings(forward = false, withItself = false).first { it !is PsiWhiteSpace }
    return PsiChildRange(first, newLast)
}