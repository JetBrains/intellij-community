// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class ConvertCollectionConstructorToFunction :
    KotlinApplicableModCommandAction<KtCallExpression, ConvertCollectionConstructorToFunction.Context>(KtCallExpression::class) {

    @JvmInline
    value class Context(val functionName: String)

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.collection.constructor.to.function")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.valueArguments.isEmpty() && COLLECTION_SHORT_NAMES.contains(element.calleeExpression?.text)

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val symbol = element.resolveToCall()?.successfulConstructorCallOrNull()?.symbol ?: return null
        val fqName = symbol.containingClassId?.asFqNameString() ?: return null
        val functionName = COLLECTION_CTOR_TO_FUNC[fqName] ?: return null
        return Context(functionName)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val callee = element.calleeExpression ?: return
        callee.replace(KtPsiFactory(actionContext.project).createExpression(elementContext.functionName))
        element.getQualifiedExpressionForSelector()?.replace(element)?.also {
            updater.moveCaretTo(it)
        }
    }
}

private val COLLECTION_CTOR_TO_FUNC: Map<String, String> = hashMapOf(
    "java.util.ArrayList" to "arrayListOf",
    "kotlin.collections.ArrayList" to "arrayListOf",
    "java.util.HashMap" to "hashMapOf",
    "kotlin.collections.HashMap" to "hashMapOf",
    "java.util.HashSet" to "hashSetOf",
    "kotlin.collections.HashSet" to "hashSetOf",
    "java.util.LinkedHashMap" to "linkedMapOf",
    "kotlin.collections.LinkedHashMap" to "linkedMapOf",
    "java.util.LinkedHashSet" to "linkedSetOf",
    "kotlin.collections.LinkedHashSet" to "linkedSetOf"
)

private val COLLECTION_SHORT_NAMES: Set<String> =
    COLLECTION_CTOR_TO_FUNC.keys.asSequence().map { it.substringAfterLast('.') }.toSet()
