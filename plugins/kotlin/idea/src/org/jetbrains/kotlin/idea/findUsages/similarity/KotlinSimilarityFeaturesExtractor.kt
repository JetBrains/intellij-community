// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.psi.PsiElement
import com.intellij.usages.similarity.bag.Bag
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder
import org.jetbrains.kotlin.psi.*

class KotlinSimilarityFeaturesExtractor(element: PsiElement, context: PsiElement) : KtTreeVisitorVoid() {
    private val myUsageSimilarityFeaturesRecorder = UsageSimilarityFeaturesRecorder(context, element)
    private val myContext = context
    private val myVariableNames = HashSet<String>()

    fun getFeatures(): Bag {
        collectVariableNames()
        myContext.accept(this)
        return myUsageSimilarityFeaturesRecorder.features
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "literal: string")
        super.visitStringTemplateExpression(expression)
    }

    override fun visitProperty(property: KtProperty) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(property, "PROPERTY: ")
        super.visitProperty(property)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val calleeExpression = expression.calleeExpression
        if (calleeExpression is KtNameReferenceExpression) {
            myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "{CALL: ${calleeExpression.getReferencedName()}}")
        }
        super.visitCallExpression(expression)
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, expression.operationToken.toString())
        super.visitBinaryExpression(expression)
    }

    override fun visitIsExpression(expression: KtIsExpression) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "instanceof")
        super.visitIsExpression(expression)
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(lambdaExpression, "lambda")
        super.visitLambdaExpression(lambdaExpression)
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, expression.operationToken.toString())
        super.visitUnaryExpression(expression)
    }

    override fun visitTypeReference(typeReference: KtTypeReference) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(typeReference, "TYPE: ${typeReference.nameForReceiverLabel()}")
        super.visitTypeReference(typeReference)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        var feature = "REFERENCE: "
        val parent = expression.parent
        if (fieldOrMethodReference(expression) || parent is KtUserType) {
            if (expression is KtNameReferenceExpression) {
                feature = "{REFERENCE: ${expression.getReferencedName()}}"
            }
        }
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, feature)
        super.visitReferenceExpression(expression)
    }

    private fun fieldOrMethodReference(expression: KtReferenceExpression) =
        expression is KtNameReferenceExpression && !myVariableNames.contains(expression.getReferencedName())

    private fun collectVariableNames() {
        var currentElement = myContext
        while (true) {
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
            entry.name?.let(myVariableNames::add)
        }
    }

    private fun collectFunctionParametersNames(function: KtFunction) {
        if (function is KtFunctionLiteral) {
            myVariableNames += "it"
        }

        for (valueParameter in function.valueParameters) {
            collectParameterNames(valueParameter)
        }
    }

    private fun collectParameterNames(parameter: KtParameter) {
        parameter.name?.let(myVariableNames::add)
        parameter.destructuringDeclaration?.let(::collectDestructuringDeclarationNames)
    }

    private fun collectPropertyName(property: KtProperty) {
        property.name?.let(myVariableNames::add)
    }
}