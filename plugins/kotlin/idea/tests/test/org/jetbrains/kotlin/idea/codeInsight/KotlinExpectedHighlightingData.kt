// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiFile
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.idea.codeInsight.InnerLineMarkerConfiguration.Companion.sanitizeLineMarker
import java.util.*
import java.util.function.ToIntFunction

class KotlinExpectedHighlightingData(document: Document) :
    ExpectedHighlightingData(/* document = */ document,
                             /* checkWarnings = */ false,
                             /* checkWeakWarnings = */ false,
                             /* checkInfos = */ false
    ) {

    override fun checkLineMarkers(psiFile: PsiFile?, markerInfos: MutableCollection<out LineMarkerInfo<*>>, text: String) {
        val fileName = if (psiFile == null) "" else psiFile.name + ": "
        val failMessage = StringBuilder()
        for (info in markerInfos) {
            if (!containsLineMarker(info, myLineMarkerInfos.values)) {
                if (failMessage.isNotEmpty()) failMessage.append('\n')
                failMessage.append(fileName).append("extra ")
                    .append(rangeString(text, info.startOffset, info.endOffset))
                    .append(": '").append(sanitizeLineMarker(info.lineMarkerTooltip)).append('\'')
                val icon = info.icon
                if (icon != null && icon.toString() != ANY_TEXT) {
                    failMessage.append(" icon='").append(icon).append('\'')
                }
            }
        }
        for (expectedLineMarker in myLineMarkerInfos.values) {
            if (markerInfos.isEmpty() || !containsLineMarker(expectedLineMarker, markerInfos)) {
                if (failMessage.isNotEmpty()) failMessage.append('\n')
                failMessage.append(fileName).append("missing ")
                    .append(rangeString(text, expectedLineMarker.startOffset, expectedLineMarker.endOffset))
                    .append(": '").append(sanitizeLineMarker(expectedLineMarker.lineMarkerTooltip)).append('\'')
                val icon = expectedLineMarker.icon
                if (icon != null && icon.toString() != ANY_TEXT) {
                    failMessage.append(" icon='").append(icon).append('\'')
                }
            }
        }
        if (failMessage.isNotEmpty()) {
            var filePath: String? = null
            if (psiFile != null) {
                val file = psiFile.virtualFile
                if (file != null) {
                    filePath = file.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH)
                }
            }
            throw FileComparisonFailure(failMessage.toString(), myText, getActualLineMarkerFileText(markerInfos), filePath)
        }
    }

    override fun containsLineMarker(info: LineMarkerInfo<*>?, where: MutableCollection<out LineMarkerInfo<*>>?): Boolean {
        val originalTooltip = info!!.lineMarkerTooltip
        val infoTooltip = sanitizeLineMarker(originalTooltip)
        val icon = info.icon
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

        return false
    }

    private fun getActualLineMarkerFileText(markerInfos: Collection<LineMarkerInfo<*>>): String {
        val result = java.lang.StringBuilder()
        var index = 0
        val lineMarkerInfos: MutableList<Pair<LineMarkerInfo<*>, Int>?> = ArrayList(markerInfos.size * 2)
        for (info in markerInfos) lineMarkerInfos.add(Pair.create(info, info.startOffset))
        for (info in markerInfos) lineMarkerInfos.add(Pair.create(info, info.endOffset))
        lineMarkerInfos.subList(markerInfos.size, lineMarkerInfos.size).reverse()
        lineMarkerInfos.sortWith(
            Comparator.comparingInt<Pair<LineMarkerInfo<*>, Int>>(ToIntFunction { o: Pair<LineMarkerInfo<*>, Int> -> o.second!! }))
        val documentText = myDocument.text
        for (info in lineMarkerInfos) {
            val expectedLineMarker = info!!.first
            result.append(documentText, index, info.second)
            if (info.second == expectedLineMarker.startOffset) {
                result
                    .append("<lineMarker descr=\"")
                    .append(sanitizeLineMarker(expectedLineMarker.lineMarkerTooltip))
                    .append("\">")
            } else {
                result.append("</lineMarker>")
            }
            index = info.second
        }
        result.append(documentText, index, myDocument.textLength)
        return result.toString()
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