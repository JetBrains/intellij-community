// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.psi.PsiElement
import com.intellij.usages.similarity.bag.Bag
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder
import org.jetbrains.kotlin.psi.*

class KotlinSimilarityFeaturesExtractor(context: PsiElement) : KtTreeVisitorVoid() {
    private val myUsageSimilarityFeaturesRecorder = UsageSimilarityFeaturesRecorder(context)
    private val myContext = context

    fun getFeatures(): Bag {
        myContext.accept(this)
        return myUsageSimilarityFeaturesRecorder.features
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, "literal: string")
        super.visitStringTemplateExpression(expression)
    }

    override fun visitProperty(property: KtProperty) {
        myUsageSimilarityFeaturesRecorder.addAllFeatures(property, "VAR: ")
        super.visitProperty(property)
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

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        var feature = "VAR:"
        if (!theFirstReferenceInQualifiedExpression(expression)) {
            if (expression is KtNameReferenceExpression) {
                feature = "{CALL: ${expression.getReferencedName()}}"
            }
        }
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, feature)
        super.visitReferenceExpression(expression)
    }

    private fun theFirstReferenceInQualifiedExpression(expression: KtReferenceExpression) =
        (expression.parent is KtQualifiedExpression && expression.parent.firstChild == expression)
}