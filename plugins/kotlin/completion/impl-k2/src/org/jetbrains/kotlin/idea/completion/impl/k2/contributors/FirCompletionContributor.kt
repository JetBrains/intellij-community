// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext

internal interface FirCompletionContributor<C : FirRawPositionCompletionContext> {
    context(KtAnalysisSession)
    fun complete(
        positionContext: C,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    )
}