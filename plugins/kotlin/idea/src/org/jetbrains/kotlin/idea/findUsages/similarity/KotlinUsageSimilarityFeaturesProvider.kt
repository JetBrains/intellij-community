// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.usages.similarity.bag.Bag
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinUsageSimilarityFeaturesProvider : UsageSimilarityFeaturesProvider {
    @RequiresReadLock
    @RequiresBackgroundThread
    override fun getFeatures(usage: PsiElement): Bag {
        val features = Bag()
        if (!Registry.`is`("similarity.find.usages.kotlin.clustering.enable")) {
            return features
        }
        val context = getContext(usage)
        if (context is KtParameter || context is KtFunction) {
            val function = context.parentOfType<KtFunction>(withSelf = true)
            if (function is KtFunction) {
                features.addAll(collectFeaturesForFunctionSignature(function, context))
            }
        } else if (context != null) {
            features.addAll(KotlinSimilarityFeaturesExtractor(usage, context).getFeatures())
        }
        return features
    }

    private fun collectFeaturesForFunctionSignature(function: KtFunction, context: PsiElement): Bag {
        val features = Bag()
        features.add("OVERRIDE: ${function.hasModifier(KtTokens.OVERRIDE_KEYWORD)}")
        features.add("NAME: ${function.name}")
        features.add("FUNCTION_CLASS: ${function::class}")
        features.add("RETURN_TYPE: ${toFeature(function.typeReference)}")
        features.add("RECEIVER_TYPE_REFERENCE: ${function.receiverTypeReference != null}")
        function.valueParameters.forEach {
            features.add(
                "PARAMETER_TYPE: ${
                    if (it == context) "USAGE: " + toFeature(it.typeReference) else toFeature(it.typeReference)
                }"
            )
        }
        return features
    }

    private fun toFeature(typeReference: KtTypeReference?): String? {
        return typeReference?.text?.filterNot { it.isWhitespace() }
    }

    fun getContext(element: PsiElement): PsiElement? {
        return PsiTreeUtil.findFirstParent(
            element,
            false,
            Condition { e: PsiElement? ->
                e is KtStatementExpression || e?.parent is KtBlockExpression || e is KtImportDirective
            },
        )
    }
}