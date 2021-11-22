// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.rendering.*
import org.jetbrains.kotlin.idea.codeMetaInfo.models.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.HighlightingCodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.CodeMetaInfo
import org.jetbrains.kotlin.idea.codeMetaInfo.models.LineMarkerCodeMetaInfo


abstract class AbstractCodeMetaInfoRenderConfiguration(var renderParams: Boolean = true) {
    private val clickOrPressRegex = "(Click or press|Press).*(to navigate)".toRegex() // We have different hotkeys on different platforms
    open fun asString(codeMetaInfo: CodeMetaInfo) = codeMetaInfo.tag + getPlatformsString(codeMetaInfo)

    open fun getAdditionalParams(codeMetaInfo: CodeMetaInfo) = ""

    protected fun sanitizeLineMarkerTooltip(originalText: String?): String {
        if (originalText == null) return "null"
        val noHtmlTags = StringUtil.removeHtmlTags(originalText)
            .replace(" ", "")
            .replace(clickOrPressRegex, "$1 ... $2")
            .trim()
        return sanitizeLineBreaks(noHtmlTags)
    }

    protected fun sanitizeLineBreaks(originalText: String): String {
        var sanitizedText = originalText
        sanitizedText = StringUtil.replace(sanitizedText, "\r\n", " ")
        sanitizedText = StringUtil.replace(sanitizedText, "\n", " ")
        sanitizedText = StringUtil.replace(sanitizedText, "\r", " ")
        return sanitizedText
    }

    protected fun getPlatformsString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo.attributes.isEmpty()) return ""
        return "{${codeMetaInfo.attributes.joinToString(";")}}"
    }
}

open class DiagnosticCodeMetaInfoConfiguration(
    val withNewInference: Boolean = true,
    val renderSeverity: Boolean = false
) : AbstractCodeMetaInfoRenderConfiguration() {
    private val crossPlatformLineBreak = """\r?\n""".toRegex()

    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo !is DiagnosticCodeMetaInfo) return ""
        return (getTag(codeMetaInfo)
                + getPlatformsString(codeMetaInfo)
                + getParamsString(codeMetaInfo))
            .replace(crossPlatformLineBreak, "")
    }

    private fun getParamsString(codeMetaInfo: DiagnosticCodeMetaInfo): String {
        if (!renderParams) return ""
        val params = mutableListOf<String>()

        @Suppress("UNCHECKED_CAST")
        val renderer = when (codeMetaInfo.diagnostic.factory) {
            is DebugInfoDiagnosticFactory1 -> DiagnosticWithParameters1Renderer(
                "{0}",
                Renderers.TO_STRING
            ) as DiagnosticRenderer<Diagnostic>
            else -> DefaultErrorMessages.getRendererForDiagnostic(codeMetaInfo.diagnostic)
        }
        if (renderer is AbstractDiagnosticWithParametersRenderer) {
            val renderParameters = renderer.renderParameters(codeMetaInfo.diagnostic)
            params.addAll(ContainerUtil.map(renderParameters) { it.toString() })
        }
        if (renderSeverity)
            params.add("severity='${codeMetaInfo.diagnostic.severity}'")

        params.add(getAdditionalParams(codeMetaInfo))

        return "(\"${params.filter { it.isNotEmpty() }.joinToString("; ")}\")"
    }

    fun getTag(codeMetaInfo: DiagnosticCodeMetaInfo): String {
        return codeMetaInfo.diagnostic.factory.name!!
    }
}

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
