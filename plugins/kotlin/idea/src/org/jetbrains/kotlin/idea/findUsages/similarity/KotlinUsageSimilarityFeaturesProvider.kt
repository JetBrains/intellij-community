// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.findUsages.similarity

import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.similarity.bag.Bag
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtStatementExpression

class KotlinUsageSimilarityFeaturesProvider : UsageSimilarityFeaturesProvider {
    @RequiresReadLock
    @RequiresBackgroundThread
    override fun getFeatures(usage: PsiElement): Bag {
        val properties = Bag()
        if (!Registry.`is`("similarity.find.usages.kotlin.clustering.enable")) {
            return properties
        }
        val statement = getContext(usage)
        if (statement != null) {
            properties.addAll(KotlinSimilarityFeaturesExtractor(statement).getFeatures())
        }

        return properties
    }

    @RequiresReadLock
    override fun isAvailable(usageTarget: PsiElementUsageTarget): Boolean {
        return usageTarget.element is KtFunction
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