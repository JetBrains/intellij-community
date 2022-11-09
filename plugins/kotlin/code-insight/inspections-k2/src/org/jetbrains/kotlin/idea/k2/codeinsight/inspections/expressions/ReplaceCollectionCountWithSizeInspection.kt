// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
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

internal class ReplaceCollectionCountWithSizeInspection : AbstractKotlinApplicableInspection<KtCallExpression>(KtCallExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.replace.collection.count.with.size.display.name")
    override fun getActionName(element: KtCallExpression): String =
        KotlinBundle.message("replace.collection.count.with.size.quick.fix.text")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.calleeExpression?.text == "count" && element.valueArguments.isEmpty()

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtCallExpression): Boolean {
        val functionSymbol = element.resolveToFunctionSymbol() ?: return false
        val receiverClassId = (functionSymbol.receiverType as? KtNonErrorClassType)?.classId ?: return false
        return functionSymbol.callableIdIfNonLocal == COLLECTION_COUNT_CALLABLE_ID && receiverClassId in COLLECTION_CLASS_IDS
    }

    override fun apply(element: KtCallExpression, project: Project, editor: Editor?) {
        element.replace(KtPsiFactory(element).createExpression("size"))
    }
}

context(KtAnalysisSession)
private fun KtCallExpression.resolveToFunctionSymbol(): KtFunctionSymbol? =
    calleeExpression?.mainReference?.resolveToSymbol() as? KtFunctionSymbol
