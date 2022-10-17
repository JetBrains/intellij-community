// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.psi.PsiElement
import com.intellij.usages.similarity.bag.Bag
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesRecorder
import org.jetbrains.kotlin.psi.*

class KotlinSimilarityFeaturesExtractor(element: PsiElement, context: PsiElement) : KtTreeVisitorVoid() {
    private val myUsageSimilarityFeaturesRecorder = UsageSimilarityFeaturesRecorder(context, element)
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
        if (fieldOrMethodReference(parent, expression) || parent is KtUserType) {
            if (expression is KtNameReferenceExpression) {
                feature = "{REFERENCE: ${expression.getReferencedName()}}"
            }
        }
        myUsageSimilarityFeaturesRecorder.addAllFeatures(expression, feature)
        super.visitReferenceExpression(expression)
    }

    private fun fieldOrMethodReference(parent: PsiElement?, expression: KtReferenceExpression) =
        parent is KtQualifiedExpression && parent.firstChild != expression

}