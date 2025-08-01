// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.expressionOrReturnType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.Variance

class KotlinHighLevelExpressionTypeProvider : KotlinExpressionTypeProvider() {
    // this method gets called from the non-blocking read action
    @OptIn(KaExperimentalApi::class)
    override fun getInformationHint(element: KtExpression): String = analyze(element) {
        val ktType = element.expressionOrReturnType() ?: return KotlinBundle.message("type.provider.unknown.type")
        @NlsSafe
        val rendered = ktType.render(renderer = KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
        StringUtil.escapeXmlEntities(rendered)
    }

    override fun getErrorHint(): String = KotlinBundle.message("hint.text.no.expression.found")
}