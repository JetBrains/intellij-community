// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.coroutines

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.AbstractSimplifiableCallChainInspection
import org.jetbrains.kotlin.psi.KtQualifiedExpression

internal class SimplifiableFlowCallChainInspection : AbstractSimplifiableCallChainInspection() {
    override fun getProblemDescription(element: KtQualifiedExpression, context: CallChainConversion): String {
        return KotlinBundle.message("call.chain.on.flow.type.may.be.simplified")
    }

    override val potentialConversions: Map<ConversionId, List<CallChainConversion>>
        get() = FlowCallChainConversions.conversionGroups
}