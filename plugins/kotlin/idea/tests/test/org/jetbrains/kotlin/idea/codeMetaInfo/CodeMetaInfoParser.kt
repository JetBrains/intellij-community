// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeMetaInfo

import org.jetbrains.kotlin.idea.codeMetaInfo.models.ParsedCodeMetaInfo

object CodeMetaInfoParser {
    val openingRegex = """(<!([^"]*?((".*?")(, ".*?")*?)?[^"]*?)!>)""".toRegex()
    val closingRegex = """(<!>)""".toRegex()

    val openingOrClosingRegex = """(${closingRegex.pattern}|${openingRegex.pattern})""".toRegex()

    private val descriptionRegex = "\\(\".*?\"\\)".toRegex()

    /*
     * ([\S&&[^,(){}]]+) -- tag, allowing all non-space characters except bracers and curly bracers
     * ([{](.*?)[}])? -- list of platforms
     * (\("(.*?)"\))? -- arguments of meta info
     * (, )? -- possible separator between different infos
     */
    private val tagRegex = """([\S&&[^,(){}]]+)([{](.*?)[}])?(\("(.*?)"\))?(, )?""".toRegex()

    fun getCodeMetaInfoFromText(renderedText: String): List<ParsedCodeMetaInfo> {
        var text = renderedText
        val openingMatchResults = ArrayDeque<MatchResult>()
        val stackOfOpeningMatchResults = ArrayDeque<MatchResult>()
        val closingMatchResults = mutableMapOf<MatchResult, MatchResult>()
        val result = mutableListOf<ParsedCodeMetaInfo>()

        while (true) {
            var openingStartOffset = Int.MAX_VALUE
            var closingStartOffset = Int.MAX_VALUE
            val opening = openingRegex.find(text)
            val closing = closingRegex.find(text)
            if (opening == null && closing == null) break

            if (opening != null)
                openingStartOffset = opening.range.first
            if (closing != null)
                closingStartOffset = closing.range.first

            text = if (openingStartOffset < closingStartOffset) {
                requireNotNull(opening)
                openingMatchResults.addLast(opening)
                stackOfOpeningMatchResults.addLast(opening)
                text.removeRange(openingStartOffset, opening.range.last + 1)
            } else {
                requireNotNull(closing)
                closingMatchResults[stackOfOpeningMatchResults.removeLast()] = closing
                text.removeRange(closingStartOffset, closing.range.last + 1)
            }
        }
        if (openingMatchResults.size != closingMatchResults.size) {
            error("Opening and closing tags counts are not equals")
        }
        while (!openingMatchResults.isEmpty()) {
            val openingMatchResult = openingMatchResults.removeLast()
            val closingMatchResult = closingMatchResults.getValue(openingMatchResult)
            val allMetaInfos = openingMatchResult.groups[2]!!.value
            tagRegex.findAll(allMetaInfos).map { it.groups }.forEach {
                val tag = it[1]!!.value
                val platforms = it[3]?.value?.split(";") ?: emptyList()
                val description = it[5]?.value

                result.add(
                    ParsedCodeMetaInfo(
                        openingMatchResult.range.first,
                        closingMatchResult.range.first,
                        platforms.toMutableList(),
                        tag,
                        descriptionRegex.find(allMetaInfos)?.value,
                        description
                    )
                )
            }
        }
        return result
    }
}