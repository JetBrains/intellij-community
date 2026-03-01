// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.base.psi.unwrapIfLabeled
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.asAssignment
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.siblings

internal class UseWithIndexInspection : KotlinApplicableInspectionBase.Simple<KtForExpression, UseWithIndexInspection.Context>() {
    class Context(
        val indexVariable: SmartPsiElementPointer<KtProperty>,
        val initializationStatement: SmartPsiElementPointer<KtExpression>,
        val incrementExpression: SmartPsiElementPointer<KtUnaryExpression>,
    )

    override fun getProblemDescription(element: KtForExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message("inspection.use.with.index.k2.problem.description")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        object : KtVisitorVoid() {
            override fun visitForExpression(expression: KtForExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }

    override fun getApplicableRanges(element: KtForExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.forKeyword }

    override fun isApplicableByPsi(element: KtForExpression): Boolean {
        if (element.destructuringDeclaration != null) return false
        if (element.loopParameter == null) return false
        if (element.loopRange == null) return false
        if (PsiTreeUtil.findChildOfType(element, KtContinueExpression::class.java) != null) return false

        return true
    }

    override fun KaSession.prepareContext(element: KtForExpression): Context? {
        val loopRange = element.loopRange ?: return null
        if (!isExpressionTypeSupported(loopRange)) return null

        val statements = when (val body = element.body) {
            is KtBlockExpression -> body.statements
            else -> listOfNotNull(body)
        }
        val unaryExpressions = buildList {
            for (statement in statements) {
                addAll(
                    statement.collectDescendantsOfType<KtUnaryExpression>(
                    canGoInside = { it !is KtBlockExpression && it !is KtFunction }
                ))
            }
        }
        unaryExpressions.forEach { unaryExpression ->
            val variableInitializationInfo = collectVariableInitializationInfo(
                possibleIndexIncrement = unaryExpression, forLoopExpression = element, forLoopStatements = statements
            )
            if (variableInitializationInfo != null)
                return Context(
                    indexVariable = variableInitializationInfo.variableDeclaration.createSmartPointer(),
                    initializationStatement = variableInitializationInfo.initializationStatement.createSmartPointer(),
                    incrementExpression = unaryExpression.createSmartPointer(),
                )
        }

        return null
    }

    override fun createQuickFix(element: KtForExpression, context: Context): KotlinModCommandQuickFix<KtForExpression> =
        object : KotlinModCommandQuickFix<KtForExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("use.withindex.instead.of.manual.index.increment")

            override fun applyFix(project: Project, element: KtForExpression, updater: ModPsiUpdater) {
                val psiFactory = KtPsiFactory(element.project)
                val loopRange = element.loopRange ?: return
                val loopParameter = element.loopParameter ?: return

                val indexVariable = context.indexVariable.element?.let(updater::getWritable) ?: return
                val initializationStatement = context.initializationStatement.element?.let(updater::getWritable) ?: return
                val incrementExpression = context.incrementExpression.element?.let(updater::getWritable) ?: return

                val multiParameter = (psiFactory.createExpressionByPattern(
                    "for(($0, $1) in x){}",
                    indexVariable.nameAsSafeName,
                    loopParameter.text
                ) as? KtForExpression)?.loopParameter ?: return

                loopParameter.replace(multiParameter)

                val newLoopRange = psiFactory.createExpressionByPattern("$0.withIndex()", loopRange)
                loopRange.replace(newLoopRange)

                indexVariable.delete()
                initializationStatement.delete()
                incrementExpression.delete()
            }
        }

    private fun KaSession.isExpressionTypeSupported(rangeExpression: KtExpression): Boolean {
        val rangeExpressionType = rangeExpression.expressionType ?: return false
        return rangeExpressionType.isSubtypeOf(StandardClassIds.Iterable)
                || rangeExpressionType.isArrayOrPrimitiveArray
    }

    private fun KaSession.collectVariableInitializationInfo(
        possibleIndexIncrement: KtUnaryExpression,
        forLoopExpression: KtForExpression,
        forLoopStatements: List<KtExpression>,
    ): VariableInitializationInfo? {
        val incrementExpressionOperand = possibleIndexIncrement.takeIf { it.operationToken == KtTokens.PLUSPLUS }?.baseExpression
            ?: return null

        // K1 checks whether the increment is always reached via the pseudocode, not easily replicable in K2.
        // In K2 the inspection uses a heuristic: consider only top level increment statements and skip loops with `continue` in them.
        if (possibleIndexIncrement.parent != forLoopExpression.body) return null

        val variableInitializationInfo = findVariableInitializationBeforeLoop(incrementExpressionOperand, forLoopExpression)
            ?: return null

        if (variableInitializationInfo.initializer !is KtConstantExpression
            || variableInitializationInfo.initializer.text != "0"
        ) return null

        val variableDeclaration = variableInitializationInfo.variableDeclaration
        if (countWriteUsages(variableDeclaration, forLoopExpression) != 1) return null
        if (hasUsagesOutsideOfLoop(variableInitializationInfo, forLoopExpression)) return null
        if (hasUsagesAfterIncrement(possibleIndexIncrement, variableInitializationInfo, forLoopExpression, forLoopStatements)) return null

        return variableInitializationInfo
    }

    private fun hasUsagesAfterIncrement(
        possibleIndexIncrement: KtUnaryExpression,
        variableInitializationInfo: VariableInitializationInfo,
        forLoop: KtForExpression,
        forLoopStatements: List<KtExpression>,
    ): Boolean {
        val forLoopStatementsAfterIncrement = forLoopStatements.dropWhile { it != possibleIndexIncrement }.drop(1)
        return ReferencesSearch.search(variableInitializationInfo.variableDeclaration, LocalSearchScope(forLoop))
            .anyMatch { reference ->
                reference.element.parents.any { parent -> parent in forLoopStatementsAfterIncrement }
            }
    }

    private class VariableInitializationInfo(
        val variableDeclaration: KtProperty,
        val initializationStatement: KtExpression,
        val initializer: KtExpression
    )

    private fun KaSession.findVariableInitializationBeforeLoop(
        incrementExpressionOperand: KtExpression,
        forLoopExpression: KtForExpression,
    ): VariableInitializationInfo? {
        if (incrementExpressionOperand !is KtNameReferenceExpression) return null
        if (incrementExpressionOperand.getQualifiedExpressionForSelector() != null) return null

        val variableSymbol = resolveToVariable(incrementExpressionOperand) ?: return null
        val variable = variableSymbol.psi as? KtProperty ?: return null
        if (!variable.isVar) return null
        if (!variable.returnType.isIntType) return null

        val unwrappedFor = forLoopExpression.unwrapIfLabeled()
        if (unwrappedFor.parent !is KtBlockExpression) return null
        val statementsBeforeFor = unwrappedFor
            .siblings(forward = false, withItself = false)
            .filterIsInstance<KtExpression>()

        return statementsBeforeFor.firstNotNullOfOrNull { statement ->
            extractVariableInitialization(statement, variable, variableSymbol)
        }
    }

    private fun hasUsagesOutsideOfLoop(variableInitializationInfo: VariableInitializationInfo, forLoop: KtForExpression): Boolean {
        val variable = variableInitializationInfo.variableDeclaration
        val fullUsageScope = PsiSearchHelper.getInstance(variable.project).getCodeUsageScope(variable)
        val allUsagesCount = ReferencesSearch.search(variable, fullUsageScope).findAll().count()
        val loopUsagesCount = ReferencesSearch.search(variable, LocalSearchScope(forLoop)).findAll().count()
        val initUsageCount =
            if (variableInitializationInfo.initializationStatement != variableInitializationInfo.variableDeclaration) 1 else 0

        return allUsagesCount > loopUsagesCount + initUsageCount
    }

    private fun countWriteUsages(variableDeclaration: KtVariableDeclaration, inElement: KtElement): Int {
        return ReferencesSearch.search(variableDeclaration, LocalSearchScope(inElement))
            .findAll().count { reference ->
                reference is KtSimpleNameReference
                        && reference.element.readWriteAccess(useResolveForReadWrite = true).isWrite
            }
    }

    private fun KaSession.extractVariableInitialization(
        statement: KtExpression,
        variable: KtProperty,
        variableSymbol: KaVariableSymbol,
    ): VariableInitializationInfo? {
        if (statement == variable) {
            val initializer = variable.initializer ?: return null
            return VariableInitializationInfo(variable, variable, initializer)
        }

        if (variable.initializer != null) return null
        val assignment = statement.asAssignment() ?: return null
        val assignedToSymbol = resolveToVariable(assignment.left ?: return null)
        if (assignedToSymbol != variableSymbol) return null
        val initializer = assignment.right ?: return null

        return VariableInitializationInfo(variable, statement, initializer)
    }

    private fun KaSession.resolveToVariable(callExpression: KtElement): KaVariableSymbol? =
        callExpression.resolveToCall()?.successfulVariableAccessCall()?.symbol
}
