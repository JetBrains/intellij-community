// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.CleanupLocalInspectionTool
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.with
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class RedundantUnitReturnTypeInspection :
    AbstractKotlinApplicatorBasedInspection<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo>(
        KtNamedFunction::class
    ), CleanupLocalInspectionTool {

    override fun getApplicabilityRange() = ApplicabilityRanges.CALLABLE_RETURN_TYPE

    override fun getApplicator() = CallableReturnTypeUpdaterApplicator.applicator.with {
        isApplicableByPsi { callable ->
            val function = callable as? KtNamedFunction ?: return@isApplicableByPsi false
            function.hasBlockBody() && function.typeReference != null
        }
        familyName(KotlinBundle.lazyMessage("remove.explicit.type.specification"))
        actionName(KotlinBundle.lazyMessage("redundant.unit.return.type"))
    }

    override fun getInputProvider() = inputProvider<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo> { function ->
        when {
            function.getFunctionLikeSymbol().returnType.isUnit ->
                CallableReturnTypeUpdaterApplicator.TypeInfo(CallableReturnTypeUpdaterApplicator.TypeInfo.UNIT)

            else -> null
        }
    }
}
