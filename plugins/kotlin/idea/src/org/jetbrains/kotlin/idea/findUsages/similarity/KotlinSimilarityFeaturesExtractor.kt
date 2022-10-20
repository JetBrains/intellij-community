// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
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
        var scope = PsiTreeUtil.findFirstParent(myContext, true, Condition { e: PsiElement? -> e is KtFunction || e is KtFile })
        while (scope != null) {
            collectVariableNames(scope)
            scope = PsiTreeUtil.findFirstParent(scope, true, Condition { e: PsiElement? -> e is KtFunction || e is KtFile })
        }
        val containingFile = myContext.containingFile
        collectAllProperties(containingFile, myContext)
    }

    private fun collectVariableNames(
        scope: PsiElement
    ) {
        if (scope is KtFunction) {
            collectFunctionParameters(scope)
        }
        collectAllProperties(scope, myContext)
    }

    private fun collectFunctionParameters(scope: KtFunction) {
        val valueParameterList = scope.valueParameterList
        if (valueParameterList != null) {
            for (typeParameter in valueParameterList.parameters) {
                typeParameter.name?.let { myVariableNames.add(it) }
            }
        }
    }

    private fun collectAllProperties(
        scope: PsiElement,
        context: PsiElement
    ) {
        scope.accept(object : KtTreeVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                val startOffset = context.textRange.startOffset
                if (property.textRange.startOffset < startOffset) {
                    property.name?.let { myVariableNames.add(it) }
                }
                super.visitProperty(property)
            }

            override fun visitElement(element: PsiElement) {
                if (element !is KtFunction || element == scope) {
                    super.visitElement(element)
                }
            }

        })
    }

}