// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usages.similarity.bag.Bag
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.psi.*

class KotlinSimilarityFeaturesExtractor(element: PsiElement, private val context: PsiElement) : KtTreeVisitorVoid() {
    private val usageSimilarityFeaturesRecorder = UsageSimilarityFeaturesRecorder(context, element)
    private val variableNames = HashSet<String>()

    fun getFeatures(): Bag {
        collectVariableNames()
        context.accept(this)
        return usageSimilarityFeaturesRecorder.features
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        usageSimilarityFeaturesRecorder.addAllFeatures(expression, "literal: string")
        super.visitStringTemplateExpression(expression)
    }

    override fun visitProperty(property: KtProperty) {
        usageSimilarityFeaturesRecorder.addAllFeatures(property, "PROPERTY: ")
        super.visitProperty(property)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val calleeExpression = expression.calleeExpression
        if (calleeExpression is KtNameReferenceExpression) {
            usageSimilarityFeaturesRecorder.addAllFeatures(expression, "{CALL: ${calleeExpression.getReferencedName()}}")
        }
        super.visitCallExpression(expression)
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        usageSimilarityFeaturesRecorder.addAllFeatures(expression, expression.operationToken.toString())
        super.visitBinaryExpression(expression)
    }

    override fun visitIsExpression(expression: KtIsExpression) {
        usageSimilarityFeaturesRecorder.addAllFeatures(expression, "instanceof")
        super.visitIsExpression(expression)
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        usageSimilarityFeaturesRecorder.addAllFeatures(lambdaExpression, "lambda")
        super.visitLambdaExpression(lambdaExpression)
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        usageSimilarityFeaturesRecorder.addAllFeatures(expression, expression.operationToken.toString())
        super.visitUnaryExpression(expression)
    }

    override fun visitTypeReference(typeReference: KtTypeReference) {
        usageSimilarityFeaturesRecorder.addAllFeatures(typeReference, "TYPE: ${typeReference.nameForReceiverLabel()}")
        super.visitTypeReference(typeReference)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        val parent = expression.parent
        val feature = if ((fieldOrMethodReference(expression) || parent is KtUserType) && expression is KtNameReferenceExpression) {
            "{REFERENCE: ${expression.getReferencedName()}}"
        } else {
            "REFERENCE: "
        }

        usageSimilarityFeaturesRecorder.addAllFeatures(expression, feature)
        super.visitReferenceExpression(expression)
    }

    override fun visitBlockExpression(expression: KtBlockExpression) {
        if (expression.parent is KtContainerNodeForControlStructureBody && expression.statements.size > 1) {
            usageSimilarityFeaturesRecorder.addAllFeatures(expression, "COMPLEX_BODY")
        } else {
            super.visitBlockExpression(expression)
        }
    }

    private fun fieldOrMethodReference(expression: KtReferenceExpression) =
        expression is KtNameReferenceExpression && expression.getReferencedName() !in variableNames

    private fun collectVariableNames() {
        var currentElement = context
        while (currentElement !is PsiFile) {
            val parent = currentElement.parent ?: break
            val startOffsetInParent = currentElement.startOffsetInParent
            when (parent) {
                is KtBlockExpression -> collectVariableNamesFromBlock(parent, startOffsetInParent)
                is KtFunction -> collectFunctionParametersNames(parent)
                is KtWhenExpression -> collectWhenExpressionParameterName(parent)
                is KtForExpression -> collectForExpressionParameterName(parent)
            }

            currentElement = parent
        }
    }

    private fun collectForExpressionParameterName(forExpression: KtForExpression) {
        forExpression.loopParameter?.let(::collectParameterNames)
    }

    private fun collectWhenExpressionParameterName(whenExpression: KtWhenExpression) {
        val property = whenExpression.subjectExpression as? KtProperty ?: return
        collectPropertyName(property)
    }

    private fun collectVariableNamesFromBlock(blockExpression: KtBlockExpression, untilOffset: Int) {
        for (statement in blockExpression.statements) {
            if (statement.startOffsetInParent >= untilOffset) break
            when (statement) {
                is KtProperty -> collectPropertyName(statement)
                is KtDestructuringDeclaration -> collectDestructuringDeclarationNames(statement)
            }
        }
    }

    private fun collectDestructuringDeclarationNames(destructuringDeclaration: KtDestructuringDeclaration) {
        for (entry in destructuringDeclaration.entries) {
            entry.name?.let(variableNames::add)
        }
    }

    private fun collectFunctionParametersNames(function: KtFunction) {
        if (function is KtFunctionLiteral) {
            variableNames += StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
        }

        for (valueParameter in function.valueParameters) {
            collectParameterNames(valueParameter)
        }
    }

    private fun collectParameterNames(parameter: KtParameter) {
        parameter.name?.let(variableNames::add)
        parameter.destructuringDeclaration?.let(::collectDestructuringDeclarationNames)
    }

    private fun collectPropertyName(property: KtProperty) {
        property.name?.let(variableNames::add)
    }
}