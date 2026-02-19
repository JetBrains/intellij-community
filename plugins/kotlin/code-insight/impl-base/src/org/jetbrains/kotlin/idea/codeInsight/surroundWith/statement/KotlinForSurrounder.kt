// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression

internal class KotlinForSurrounder : KotlinLoopSurrounderBase<KtForExpression>() {

    override val codeTemplate: String = "for (item in items) {\n}"

    override fun getSelectionElement(loop: KtForExpression): KtExpression? = loop.loopRange

    @NlsSafe
    override fun getTemplateDescription(): String = KotlinCodeInsightBundle.message("surround.with.for.template")
}
