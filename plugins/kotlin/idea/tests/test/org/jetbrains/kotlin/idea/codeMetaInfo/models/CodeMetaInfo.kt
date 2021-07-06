// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeMetaInfo.models

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.kotlin.checkers.diagnostics.ActualDiagnostic
import org.jetbrains.kotlin.codeMetaInfo.model.CodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.model.DiagnosticCodeMetaInfo
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.AbstractCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.codeMetaInfo.renderConfigurations.DiagnosticCodeMetaInfoRenderConfiguration
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.HighlightingConfiguration
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration
import org.jetbrains.kotlin.idea.editor.fixers.end
import org.jetbrains.kotlin.idea.editor.fixers.start

class LineMarkerCodeMetaInfo(
    override val renderConfiguration: LineMarkerConfiguration,
    val lineMarker: LineMarkerInfo<*>
) : CodeMetaInfo {
    override val start: Int
        get() = lineMarker.startOffset
    override val end: Int
        get() = lineMarker.endOffset

    override val tag: String
        get() = renderConfiguration.getTag()

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderConfiguration.asString(this)
}

class HighlightingCodeMetaInfo(
    override val renderConfiguration: HighlightingConfiguration,
    val highlightingInfo: HighlightInfo
) : CodeMetaInfo {
    override val start: Int
        get() = highlightingInfo.startOffset
    override val end: Int
        get() = highlightingInfo.endOffset

    override val tag: String
        get() = renderConfiguration.getTag()

    override val attributes: MutableList<String> = mutableListOf()

    override fun asString(): String = renderConfiguration.asString(this)
}

class ParsedCodeMetaInfo(
    override val start: Int,
    override val end: Int,
    override val attributes: MutableList<String>,
    override val tag: String,
    val params: String? = null,
    val description: String?
) : CodeMetaInfo {
    override val renderConfiguration = ParsedCodeMetaInfoRenderConfiguration

    override fun asString(): String = renderConfiguration.asString(this)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is CodeMetaInfo) return false
        return this.tag == other.tag && this.start == other.start && this.end == other.end
    }

    override fun hashCode(): Int {
        var result = start
        result = 31 * result + end
        result = 31 * result + tag.hashCode()
        return result
    }

    fun copy(): ParsedCodeMetaInfo {
        return ParsedCodeMetaInfo(start, end, attributes.toMutableList(), tag, params, description)
    }
}

object ParsedCodeMetaInfoRenderConfiguration : AbstractCodeMetaInfoRenderConfiguration() {
    override fun asString(codeMetaInfo: CodeMetaInfo): String {
        require(codeMetaInfo is ParsedCodeMetaInfo)
        return super.asString(codeMetaInfo) + (codeMetaInfo.description?.let { "(\"$it\")" } ?: "")
    }
}

fun createCodeMetaInfo(obj: Any, renderConfiguration: AbstractCodeMetaInfoRenderConfiguration): List<CodeMetaInfo> {
    fun errorMessage() = "Unexpected render configuration for object $obj"
    return when (obj) {
        is Diagnostic -> {
            require(renderConfiguration is DiagnosticCodeMetaInfoConfiguration, ::errorMessage)
            obj.textRanges.map { DiagnosticCodeMetaInfo(it.start, it.end, renderConfiguration, obj) }
        }
        is ActualDiagnostic -> {
            require(renderConfiguration is DiagnosticCodeMetaInfoConfiguration, ::errorMessage)
            obj.diagnostic.textRanges.map { DiagnosticCodeMetaInfo(it.start, it.end, renderConfiguration, obj.diagnostic) }
        }
        is HighlightInfo -> {
            require(renderConfiguration is HighlightingConfiguration, ::errorMessage)
            listOf(HighlightingCodeMetaInfo(renderConfiguration, obj))
        }
        is LineMarkerInfo<*> -> {
            require(renderConfiguration is LineMarkerConfiguration, ::errorMessage)
            listOf(LineMarkerCodeMetaInfo(renderConfiguration, obj))
        }
        else -> throw IllegalArgumentException("Unknown type for creating CodeMetaInfo object $obj")
    }
}

fun getCodeMetaInfo(
    objects: List<Any>,
    renderConfiguration: AbstractCodeMetaInfoRenderConfiguration,
    filterMetaInfo: (CodeMetaInfo) -> Boolean
): List<CodeMetaInfo> {
    return objects.flatMap { createCodeMetaInfo(it, renderConfiguration) }.filter(filterMetaInfo)
}
