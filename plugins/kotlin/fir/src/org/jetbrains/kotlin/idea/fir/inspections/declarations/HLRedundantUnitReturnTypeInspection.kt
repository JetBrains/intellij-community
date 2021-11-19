// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.inspections.declarations

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.with
import org.jetbrains.kotlin.idea.fir.api.AbstractHLInspection
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.presentation
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.fir.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class HLRedundantUnitReturnTypeInspection :
    AbstractHLInspection<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo>(
        KtNamedFunction::class
    ), CleanupLocalInspectionTool {

    override val applicabilityRange = ApplicabilityRanges.CALLABLE_RETURN_TYPE

    override val applicator = CallableReturnTypeUpdaterApplicator.applicator.with {
        isApplicableByPsi { callable ->
            val function = callable as? KtNamedFunction ?: return@isApplicableByPsi false
            function.hasBlockBody() && function.typeReference != null
        }
        familyName(KotlinBundle.lazyMessage("remove.explicit.type.specification"))
        actionName(KotlinBundle.lazyMessage("redundant.unit.return.type"))
    }

    override val inputProvider = inputProvider<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo> { function ->
        when {
            function.getFunctionLikeSymbol().returnType.isUnit ->
                CallableReturnTypeUpdaterApplicator.TypeInfo(CallableReturnTypeUpdaterApplicator.TypeInfo.UNIT)
            else -> null
        }
    }

    override val presentation = presentation<KtNamedFunction> {
        highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
    }
}
