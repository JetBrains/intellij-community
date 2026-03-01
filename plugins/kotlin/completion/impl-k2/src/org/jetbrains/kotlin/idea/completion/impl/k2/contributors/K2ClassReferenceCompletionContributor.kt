// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.util.positionContext.KotlinCallableReferencePositionContext
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class K2ClassReferenceCompletionContributor : K2SimpleCompletionContributor<KotlinCallableReferencePositionContext>(
    KotlinCallableReferencePositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinCallableReferencePositionContext>)
    override fun complete() {
        if (context.positionContext.explicitReceiver == null) return
        addElement(createKeywordElement("class"))
        if (context.completionContext.targetPlatform.isJvm()) {
            addElement(createKeywordElement("class", tail = ".java"))
        }
    }
}