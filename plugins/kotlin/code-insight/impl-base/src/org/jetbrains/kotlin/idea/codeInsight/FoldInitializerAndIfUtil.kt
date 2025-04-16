// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.endOffset
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.expressionComparedToNull
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

data class FoldInitializerAndIfExpressionData(
    val initializer: KtExpression,
    val variableDeclaration: KtVariableDeclaration,
    val ifNullExpression: KtExpression,
    val typeChecked: KtTypeReference? = null,
    val variableTypeString: String?,
    val couldBeVal: Boolean = false,
)

context(KaSession)
@ApiStatus.Internal
@OptIn(KaExperimentalApi::class)
fun prepareData(element: KtIfExpression, enforceNonNullableTypeIfPossible: Boolean = false): FoldInitializerAndIfExpressionData? {
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
        val checkedType = typeReference.type
        val variableType = variableDeclaration.returnType
        if (!checkedType.isPossiblySubTypeOf(variableType)) return null
    }

    if (statement.expressionType?.isNothingType != true) return null

    if (ReferencesSearch.search(variableDeclaration, LocalSearchScope(statement)).findFirst() != null) {
        return null
    }

    val couldBeVal = variableDeclaration.isVar &&
            variableDeclaration
                .diagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
                .any { it is KaFirDiagnostic.CanBeVal }

    val type = calculateType(variableDeclaration, element, initializer, couldBeVal && enforceNonNullableTypeIfPossible)?.let { type ->
        if (type is KaErrorType) null
        else type.render(position = Variance.OUT_VARIANCE)
    }

    return FoldInitializerAndIfExpressionData(
        initializer,
        variableDeclaration,
        statement,
        typeReference,
        type,
        couldBeVal
    )
}

@ApiStatus.Internal
fun joinLines(
    element: KtIfExpression,
    variableDeclaration: KtVariableDeclaration,
    initializer: KtExpression,
    ifNullExpr: KtExpression,
    typeChecked: KtTypeReference?,
    variableTypeString: String?
): KtBinaryExpression {
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

context(KaSession)
private fun calculateType(
    declaration: KtVariableDeclaration,
    element: KtIfExpression,
    initializer: KtExpression,
    enforceNonNullableType: Boolean
) = when {
    // for var with no explicit type, add it so that the actual change won't change
    declaration.isVar && declaration.typeReference == null -> {
        if (element.condition is KtBinaryExpression) {
            val ifEndOffset = element.endOffset

            val isUsedAsNotNullable = ReferencesSearch.search(declaration, LocalSearchScope(declaration.parent)).asIterable().any {
                if (it.element.startOffset <= ifEndOffset) return@any false
                !(it.element.safeAs<KtExpression>()?.expressionType?.isMarkedNullable ?: return@any false)
            }

            if (isUsedAsNotNullable) null else initializer.expressionType

        } else {
            initializer.expressionType
        }
    }

    // for val with explicit type, change it to non-nullable
    declaration.typeReference != null && (!declaration.isVar || declaration.isVar && enforceNonNullableType) ->
        initializer.expressionType?.withNullability(KaTypeNullability.NON_NULLABLE)

    else -> null
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

@ApiStatus.Internal
fun KtValVarKeywordOwner.replaceVarWithVal(): PsiElement? {
    val varKeyword = valOrVarKeyword ?: return null
    return varKeyword.replace(KtPsiFactory(project).createValKeyword())
}