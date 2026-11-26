// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.analysis.api.dataflow.smartCastInfo
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.containsErrorType
import org.jetbrains.kotlin.idea.codeinsight.utils.expressionOrReturnType
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.psi.KtExpression

class KotlinHighLevelExpressionTypeProvider : KotlinExpressionTypeProvider() {
    private val renderer: KaRenderer = KaRenderer.withShortNames

    // this method gets called from the non-blocking read action
    override fun getInformationHint(element: KtExpression): String =
        analyze(element) {
            val ktType = element.expressionOrReturnType()
            if (ktType == null || ktType.containsErrorType()) return KotlinBundle.message("type.provider.unknown.type")
            val smartCastInfo = element.smartCastInfo
            if (smartCastInfo == null) {
                @NlsSafe
                val rendered = renderer.renderToString(ktType)
                KotlinBundle.message(
                    "type.provider.declared.type",
                    StringUtil.escapeXmlEntities(rendered)
                )
            } else {
                @NlsSafe
                val original = renderer.renderToString(smartCastInfo.originalType)
                @NlsSafe
                val smartCasted = renderer.renderToString(smartCastInfo.smartCastType)

                KotlinBundle.message(
                    "type.provider.expression.smartcast.type",
                    StringUtil.escapeXmlEntities(original),
                    StringUtil.escapeXmlEntities(smartCasted)
                )
            }

        }

    override fun getErrorHint(): String = KotlinBundle.message("hint.text.no.expression.found")

}