// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyze
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicableModCommandIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>
) : AbstractKotlinApplicableModCommandIntentionBase<ELEMENT>(elementType),
    KotlinApplicableTool<ELEMENT> {

    override fun isElementApplicable(
        element: ELEMENT,
        context: ActionContext,
    ): Boolean = super.isElementApplicable(element, context)
            && isApplicableWithAnalyze(element)

    abstract fun apply(element: ELEMENT, context: ActionContext, updater: ModPsiUpdater)

    final override fun invoke(context: ActionContext, element: ELEMENT, updater: ModPsiUpdater) {
        apply(element, context, updater)
    }
}