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
            context.parentOfType<KtFunction>(withSelf = true)?.let {
                features.addAll(collectFeaturesForFunctionSignature(it, context))
            }
        } else if (context != null) {
            features.addAll(KotlinSimilarityFeaturesExtractor(usage, context).getFeatures())
        }

        return features
    }

    private fun collectFeaturesForFunctionSignature(function: KtFunction, context: PsiElement): Bag = Bag().apply {
        add("OVERRIDE: ${function.hasModifier(KtTokens.OVERRIDE_KEYWORD)}")
        add("NAME: ${function.name}")
        add("FUNCTION_CLASS: ${function::class}")
        add("RETURN_TYPE: ${toFeature(function.typeReference)}")
        add("RECEIVER_TYPE_REFERENCE: ${function.receiverTypeReference != null}")
        function.valueParameters.forEach {
            add("PARAMETER_TYPE: ${(if (it == context) "USAGE: " else "") + toFeature(it.typeReference)}")
        }
    }

    private fun toFeature(typeReference: KtTypeReference?): String? = typeReference?.text?.filterNot { it.isWhitespace() }

    fun getContext(element: PsiElement): PsiElement? = PsiTreeUtil.findFirstParent(
        element,
        false,
        Condition { e: PsiElement? ->
            e is KtStatementExpression || e?.parent is KtBlockExpression || e is KtImportDirective
        },
    )
}