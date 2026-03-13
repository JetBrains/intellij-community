// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtWhileExpression

internal class KotlinWhileSurrounder : KotlinLoopSurrounderBase<KtWhileExpression>() {

    override val codeTemplate: String = "while (true) {\n}"

    override fun getSelectionElement(loop: KtWhileExpression): KtExpression? = loop.condition

    @NlsSafe
    override fun getTemplateDescription(): String = KotlinCodeInsightBundle.message("surround.with.while.template")
}
