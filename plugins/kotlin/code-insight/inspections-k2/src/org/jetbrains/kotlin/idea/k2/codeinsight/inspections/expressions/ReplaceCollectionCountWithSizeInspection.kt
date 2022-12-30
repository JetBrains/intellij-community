// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

private val COLLECTION_COUNT_CALLABLE_ID = CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("count"))
private val COLLECTION_CLASS_IDS = setOf(StandardClassIds.Collection, StandardClassIds.Array, StandardClassIds.Map) +
        StandardClassIds.elementTypeByPrimitiveArrayType.keys + StandardClassIds.unsignedArrayTypeByElementType.keys

internal class ReplaceCollectionCountWithSizeInspection :
    AbstractKotlinApplicatorBasedInspection<KtCallExpression, KotlinApplicatorInput.Empty>(KtCallExpression::class) {

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtCallExpression, KotlinApplicatorInput.Empty> =
        inputProvider { expression: KtCallExpression ->
            val functionSymbol = resolveToFunctionSymbol(expression) ?: return@inputProvider null
            val receiverClassId = (functionSymbol.receiverType as? KtNonErrorClassType)?.classId ?: return@inputProvider null
            if (functionSymbol.callableIdIfNonLocal == COLLECTION_COUNT_CALLABLE_ID && receiverClassId in COLLECTION_CLASS_IDS) {
                KotlinApplicatorInput.Empty
            } else {
                null
            }
        }

    override fun getApplicator(): KotlinApplicator<KtCallExpression, KotlinApplicatorInput.Empty> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("replace.collection.count.with.size.quick.fix.text"))
        isApplicableByPsi { expression ->
            expression.calleeExpression?.text == "count" && expression.valueArguments.isEmpty()
        }
        applyTo { expression, _ ->
            expression.replace(KtPsiFactory(expression).createExpression("size"))
        }
    }
}

private fun KtAnalysisSession.resolveToFunctionSymbol(expression: KtCallExpression): KtFunctionSymbol? =
    expression.calleeExpression?.mainReference?.resolveToSymbol() as? KtFunctionSymbol
