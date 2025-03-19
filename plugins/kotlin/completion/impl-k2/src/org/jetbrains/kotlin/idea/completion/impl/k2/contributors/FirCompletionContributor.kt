// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext

internal interface FirCompletionContributor<C : KotlinRawPositionContext> {

    context(KaSession)
    fun complete(
        positionContext: C,
        weighingContext: WeighingContext,
    )
}