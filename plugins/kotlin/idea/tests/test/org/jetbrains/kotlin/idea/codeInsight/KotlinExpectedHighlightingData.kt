// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.testFramework.ExpectedHighlightingData
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.idea.codeInsight.InnerLineMarkerConfiguration.Companion.sanitizeLineMarker

class KotlinExpectedHighlightingData(document: Document) :
    ExpectedHighlightingData(/* document = */ document,
                             /* checkWarnings = */ false,
                             /* checkWeakWarnings = */ false,
                             /* checkInfos = */ false
    ) {

    override fun containsLineMarker(info: LineMarkerInfo<*>?, where: MutableCollection<out LineMarkerInfo<PsiElement>>?): Boolean {
        val originalTooltip = info!!.lineMarkerTooltip
        val infoTooltip = sanitizeLineMarker(originalTooltip)
        val icon = info.icon
        try {
            for (markerInfo in where!!) {
                if (!(markerInfo.startOffset == info.startOffset && markerInfo.endOffset == info.endOffset)) continue
                val lineMarkerTooltip = markerInfo.lineMarkerTooltip
                val sanitizeLineMarker = sanitizeLineMarker(lineMarkerTooltip)
                if ((matchDescriptions(false, originalTooltip, lineMarkerTooltip) ||
                            matchDescriptions(false, infoTooltip, lineMarkerTooltip) ||
                            matchDescriptions(false, originalTooltip, sanitizeLineMarker) ||
                            matchDescriptions(false, infoTooltip, sanitizeLineMarker)) &&
                    matchIcons(icon, markerInfo.icon)
                ) {
                    return true
                }
            }
        } catch (e: Exception) {
            throw Exception("infoTooltip\n$infoTooltip\n-> ${e.message}", e)
        }

        return false
    }
}

class InnerLineMarkerConfiguration: AbstractCodeMetaInfoRenderConfiguration() {
    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        if (codeMetaInfo !is InnerLineMarkerCodeMetaInfo) return ""
        val params = mutableListOf<String>()

        codeMetaInfo.lineMarker.lineMarkerTooltip?.apply {
            params.add("descr='${sanitizeLineMarkerTooltip(this)}'")
        }

        params.add(getAdditionalParams(codeMetaInfo))

        return params.filter { it.isNotEmpty() }.joinToString("; ")
    }

    fun sanitizeLineMarker(tooltip: String?) = sanitizeLineMarkerTooltip(tooltip)

    companion object {
        val configuration = InnerLineMarkerConfiguration()

        fun sanitizeLineMarker(tooltip: String?): String {
            if (tooltip == "X") {
                throw Exception("X")
            }
            return configuration.sanitizeLineMarker(tooltip)
        }
    }

}

class InnerLineMarkerCodeMetaInfo(
    override val renderConfiguration: AbstractCodeMetaInfoRenderConfiguration,
    val lineMarker: LineMarkerInfo<*>
) : CodeMetaInfo {
    override val start: Int
        get() = lineMarker.startOffset
    override val end: Int
        get() = lineMarker.endOffset

    override val tag: String = "LINE_MARKER"

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderConfiguration.asString(this)
}