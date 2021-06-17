// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.diagnostics.rendering.*
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.LineMarkerCodeMetaInfo


open class LineMarkerConfiguration(var renderDescription: Boolean = true) : AbstractCodeMetaInfoRenderConfiguration() {
    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo !is LineMarkerCodeMetaInfo) return ""
        return getTag() + getPlatformsString(codeMetaInfo) + getParamsString(codeMetaInfo)
    }

    fun getTag() = "LINE_MARKER"

    private fun getParamsString(lineMarkerCodeMetaInfo: LineMarkerCodeMetaInfo): String {
        if (!renderParams) return ""
        val params = mutableListOf<String>()

        if (renderDescription) {
            lineMarkerCodeMetaInfo.lineMarker.lineMarkerTooltip?.apply {
                params.add("descr='${sanitizeLineMarkerTooltip(this)}'")
            }
        }

        params.add(getAdditionalParams(lineMarkerCodeMetaInfo))

        val paramsString = params.filter { it.isNotEmpty() }.joinToString("; ")
        return if (paramsString.isEmpty()) "" else "(\"$paramsString\")"
    }

    protected fun getPlatformsString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo.attributes.isEmpty()) return ""
        return "{${codeMetaInfo.attributes.joinToString(";")}}"
    }
}

open class HighlightingConfiguration(
    val renderDescription: Boolean = true,
    val renderTextAttributesKey: Boolean = true,
    val renderSeverity: Boolean = true,
    val severityLevel: HighlightSeverity = HighlightSeverity.INFORMATION,
    val checkNoError: Boolean = false
) : AbstractCodeMetaInfoRenderConfiguration() {

    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo !is HighlightingCodeMetaInfo) return ""
        return getTag() + getPlatformsString(codeMetaInfo) + getParamsString(codeMetaInfo)
    }

    fun getTag() = "HIGHLIGHTING"

    private fun getParamsString(highlightingCodeMetaInfo: HighlightingCodeMetaInfo): String {
        if (!renderParams) return ""

        val params = mutableListOf<String>()
        if (renderSeverity)
            params.add("severity='${highlightingCodeMetaInfo.highlightingInfo.severity}'")
        if (renderDescription)
            params.add("descr='${sanitizeLineBreaks(highlightingCodeMetaInfo.highlightingInfo.description)}'")
        if (renderTextAttributesKey)
            highlightingCodeMetaInfo.highlightingInfo.forcedTextAttributesKey?.apply {
                params.add("textAttributesKey='${this}'")
            }
            params.add(getAdditionalParams(highlightingCodeMetaInfo))
        val paramsString = params.filter { it.isNotEmpty() }.joinToString("; ")

        return if (paramsString.isEmpty()) "" else "(\"$paramsString\")"
    }
}
