// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FoldInitializerAndIfToElvisInspection :
    AbstractKotlinApplicableInspectionWithContext<KtIfExpression, FoldInitializerAndIfToElvisInspection.IfExpressionData>() {

    data class IfExpressionData(
        val initializer: KtExpression,
        val variableDeclaration: KtVariableDeclaration,
        val ifNullExpression: KtExpression,
        val typeChecked: KtTypeReference? = null,
        val variableTypeString: String?
    )

    override fun getProblemHighlightType(element: KtIfExpression, context: IfExpressionData): ProblemHighlightType {
        return when (element.condition) {
            is KtBinaryExpression -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            else -> ProblemHighlightType.INFORMATION
        }
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtIfExpression): IfExpressionData? {
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
        val typeReference = (operationExpression as? KtIsExpression)?.typeReference

        if (typeReference != null) {
            val checkedType = typeReference.getKtType()
            val variableType = variableDeclaration.getReturnKtType()
            if (!checkedType.isPossiblySubTypeOf(variableType)) return null
        }

        val statement = (if (then is KtBlockExpression) then.statements.singleOrNull() else then) ?: return null

        if (statement.getKtType()?.isNothing != true) return null

        if (ReferencesSearch.search(variableDeclaration, LocalSearchScope(statement)).findFirst() != null) {
            return null
        }

        val type = calculateType(variableDeclaration, element, initializer)?.let { type ->
            if (type is KtErrorType) null
            else type.render(position = Variance.OUT_VARIANCE)
        }

        return IfExpressionData(
            initializer,
            variableDeclaration,
            statement,
            typeReference,
            type
        )
    }

    override fun apply(element: KtIfExpression, context: IfExpressionData, project: Project, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(element.project)

        val variableDeclaration = updater.getWritable(context.variableDeclaration)
        val initializer = updater.getWritable(context.initializer)
        val ifNullExpr = updater.getWritable(context.ifNullExpression)
        val typeChecked = updater.getWritable(context.typeChecked)

        val childRangeBefore = PsiChildRange(variableDeclaration, element)
        val commentSaver = CommentSaver(childRangeBefore)
        val childRangeAfter = childRangeBefore.withoutLastStatement()

        val elvis = createElvisExpression(initializer, ifNullExpr, typeChecked, factory)

        val positionedElvis = initializer.replaced(elvis)
        element.delete()

        if (context.variableTypeString != null) {
            val typeReference = factory.createType(context.variableTypeString)
            variableDeclaration.setTypeReference(typeReference)?.let { shortenReferences(it) }
        }

        commentSaver.restore(childRangeAfter)

        variableDeclaration.reformat()

        positionedElvis.right?.textOffset?.let { updater.moveCaretTo(it) }
    }

    override fun getProblemDescription(element: KtIfExpression, context: IfExpressionData) =
        KotlinBundle.message("if.null.return.break.foldable.to")

    override fun getActionFamilyName() = KotlinBundle.message("replace.if.with.elvis.operator")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitIfExpression(expression: KtIfExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtIfExpression> = applicabilityRange { ifExpression ->
        val rightOffset = ifExpression.rightParenthesis?.endOffset

        if (rightOffset == null) {
            ifExpression.ifKeyword.textRangeIn(ifExpression)
        } else {
            TextRange(ifExpression.ifKeyword.startOffset, rightOffset).shiftLeft(ifExpression.startOffset)
        }
    }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        fun KtExpression.isElvisExpression(): Boolean = this is KtBinaryExpression && operationToken == KtTokens.ELVIS

        val prevStatement = (element.siblings(forward = false, withItself = false)
            .firstIsInstanceOrNull<KtExpression>() ?: return false) as? KtVariableDeclaration

        val initializer = prevStatement?.initializer ?: return false

        if (initializer.isMultiLine()) return false

        return !initializer.anyDescendantOfType<KtExpression> {
            it is KtThrowExpression || it is KtReturnExpression || it is KtBreakExpression ||
                    it is KtContinueExpression || it is KtIfExpression || it is KtWhenExpression ||
                    it is KtTryExpression || it is KtLambdaExpression || it.isElvisExpression()
        }
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

    private fun PsiChildRange.withoutLastStatement(): PsiChildRange {
        val newLast = last!!.siblings(forward = false, withItself = false).first { it !is PsiWhiteSpace }
        return PsiChildRange(first, newLast)
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
}
