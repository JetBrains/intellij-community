// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class FirClassReferenceCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int
) : FirCompletionContributorBase<KotlinCallableReferencePositionContext>(basicContext, priority) {
    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinCallableReferencePositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        if (positionContext.explicitReceiver == null) return
        sink.addElement(createKeywordElement("class"))
        if (targetPlatform.isJvm()) {
            sink.addElement(createKeywordElement("class", tail = ".java"))
        }
    }
}
