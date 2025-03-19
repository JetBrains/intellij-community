// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveIsFromIsEnumEntryFix
import org.jetbrains.kotlin.idea.quickfix.ReplaceIsEnumEntryWithComparisonFix
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object IsEnumEntryFixFactory {

    val factory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IsEnumEntry ->
        val element = diagnostic.psi.safeAs<KtTypeReference>()?.parent ?: return@ModCommandBased emptyList()

        listOfNotNull(
            when (element) {
                is KtIsExpression -> if (element.typeReference == null) null else ReplaceIsEnumEntryWithComparisonFix(element)
                is KtWhenConditionIsPattern -> if (element.typeReference == null || element.isNegated) null else RemoveIsFromIsEnumEntryFix(element)
                else -> null
            }
        )
    }
}