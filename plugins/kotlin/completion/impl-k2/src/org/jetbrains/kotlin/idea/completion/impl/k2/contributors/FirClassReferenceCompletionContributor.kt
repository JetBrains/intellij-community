// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class FirClassReferenceCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinCallableReferencePositionContext>(parameters, sink, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KotlinCallableReferencePositionContext,
        weighingContext: WeighingContext,
    ) {
        if (positionContext.explicitReceiver == null) return
        sink.addElement(createKeywordElement("class"))
        if (targetPlatform.isJvm()) {
            sink.addElement(createKeywordElement("class", tail = ".java"))
        }
    }
}
