// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class ConvertCollectionConstructorToFunction : SelfTargetingIntention<KtCallExpression>(
    KtCallExpression::class.java, KotlinBundle.messagePointer("convert.collection.constructor.to.function")
) {

    @SafeFieldForPreview
    private val functionMap = hashMapOf(
        "java.util.ArrayList.<init>" to "arrayListOf",
        "kotlin.collections.ArrayList.<init>" to "arrayListOf",
        "java.util.HashMap.<init>" to "hashMapOf",
        "kotlin.collections.HashMap.<init>" to "hashMapOf",
        "java.util.HashSet.<init>" to "hashSetOf",
        "kotlin.collections.HashSet.<init>" to "hashSetOf",
        "java.util.LinkedHashMap.<init>" to "linkedMapOf",
        "kotlin.collections.LinkedHashMap.<init>" to "linkedMapOf",
        "java.util.LinkedHashSet.<init>" to "linkedSetOf",
        "kotlin.collections.LinkedHashSet.<init>" to "linkedSetOf"
    )

    override fun isApplicableTo(element: KtCallExpression, caretOffset: Int): Boolean {
        val fq = element.resolveToCall()?.resultingDescriptor?.fqNameSafe?.asString() ?: return false
        return functionMap.containsKey(fq) && element.valueArguments.size == 0
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val fq = element.resolveToCall()?.resultingDescriptor?.fqNameSafe?.asString() ?: return
        val toCall = functionMap[fq] ?: return
        val callee = element.calleeExpression ?: return
        callee.replace(KtPsiFactory(element.project).createExpression(toCall))
        element.getQualifiedExpressionForSelector()?.replace(element)
    }
}
