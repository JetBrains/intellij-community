// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

internal interface K2ChainCompletionContributor {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinExpressionNameReferencePositionContext>)
    fun createChainedLookupElements(
        receiverExpression: KtDotQualifiedExpression,
        nameToImport: FqName,
    ): Sequence<LookupElement>
}