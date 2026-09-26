// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinDoWhileSurrounder : KotlinLoopSurrounderBase<KtDoWhileExpression>() {

    override val codeTemplate: String = "do {\n} while (true)"

    override fun getSelectionElement(loop: KtDoWhileExpression): KtExpression? = loop.condition

    @NlsSafe
    override fun getTemplateDescription(): String = KotlinCodeInsightBundle.message("surround.with.do.while.template")
}
