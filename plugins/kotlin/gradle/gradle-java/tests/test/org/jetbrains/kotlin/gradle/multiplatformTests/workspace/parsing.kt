// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

import kotlin.math.abs

class WorkspaceModelTestReportParser(private val lines: List<String>) {
    // index of the line that WASN'T processed yet
    private var curLine: Int = 0

    // indent of curLine
    private var currentIndentationLevel: Int = 0

    fun parseProjectReport(): ProjectReport {
        val comments = consumeAllCommentsAndNonMeaningfulLines()
        require(peekNextMeaningful() == PROJECT_REPORT_START) {
            "Parsing error at line ${curLine + 1}. Expected $PROJECT_REPORT_START, got ${peekNextMeaningful()}. Full text:\n" +
                    lines.joinToString()
        }

        val projectReportStart = next()

        val moduleReports = generateSequence { parseModuleReport() }.toList()

        val maybeTestConfigurationStart = nextMeaningful()
        val testConfigurationDescription = mutableListOf<String>()
        if (maybeTestConfigurationStart != null) {
            // Shouldn't happen currently, as we're if it's not exactly TEST_CONFIGURATION_DESCRIPTION_START,
            // then it will be parsed as a moduleReport. But checking just in case.
            require(maybeTestConfigurationStart == TEST_CONFIGURATION_DESCRIPTION_START) {
                "Parsing error at line ${curLine + 1}}: expected Test Configuration Description " +
                        "start ('$TEST_CONFIGURATION_DESCRIPTION_START'), but got ${maybeTestConfigurationStart}"
            }
            testConfigurationDescription.addAll(consumeMeaningfulLinesWhile { !end() }.map { it.removePrefix("- ") })
        }
        return ProjectReport(moduleReports, testConfigurationDescription).apply { addComments(comments) }
    }

    private fun parseModuleReport(): ModuleReport? {
        val comments = consumeAllCommentsAndNonMeaningfulLines()

        // Be careful, maybe modules ended and the next meaningful line is 'Test configuration:'
        val moduleName = peekNextMeaningful() ?: return null
        if (moduleName == TEST_CONFIGURATION_DESCRIPTION_START) return null

        val expectedSubblockIndent = currentIndentationLevel + INDENTATION_STEP
        // It's definitely moduleName, can advance cursor
        next()

        val moduleReportDatas = mutableListOf<ModuleReportData>()
        while (currentIndentationLevel == expectedSubblockIndent) {
            moduleReportDatas += parseModuleReportData(expectedSubblockIndent) ?: break
        }

        return ModuleReport(moduleName, moduleReportDatas).apply { addComments(comments) }
    }

    private fun parseModuleReportData(expectedIndentLevel: Int): ModuleReportData? {
        val comments = consumeAllCommentsAndNonMeaningfulLines()
        if (comments.isNotEmpty() && expectedIndentLevel != currentIndentationLevel) {
            error(
                "Parsing error at ${curLine + 1}. Some comments were parsed, but subsequent module report data wasn't found. Either:\n" +
                        "- unexpected end of input reached\n" +
                        "- there's a malformed indentation (e.g. the module report data is at the line, but with less indentation than comment)\n" +
                        "- it's a 'dangling comment'. All comments should go exactly before some actual report line on the same indentation level."
            )
        }
        if (end() || expectedIndentLevel != currentIndentationLevel) return null
        return ModuleReportData(next()).apply { addComments(comments) }
    }

    private fun consumeAllCommentsAndNonMeaningfulLines(): List<String> =
        consumeMeaningfulLinesWhile { it.trimStart().startsWith(LINE_COMMENT_START_SEPARATOR) }

    private fun consumeMeaningfulLinesWhile(condition: (String) -> Boolean): List<String> {
        val result = mutableListOf<String>()
        var cur = peekNextMeaningful()
        while (cur != null && condition(cur)) {
            result += next()
            cur = peekNextMeaningful()
        }
        return result
    }

    private fun nextMeaningful(): String? {
        skipNonMeaningful()
        return if (!end()) next() else null
    }

    private fun peekNextMeaningful(): String? {
        skipNonMeaningful()
        return if (!end()) peek() else null
    }

    private fun skipNonMeaningful() {
        while (!end() && !peek().isMeaningful()) {
            next()
        }
    }

    private fun String.isMeaningful() = isNotEmpty() && isNotBlank() && this != System.lineSeparator()

    private fun next(): String {
        val result = lines[curLine++].trim()
        if (end()) return result

        val newIndent = lines[curLine].takeWhile { it.isWhitespace() }.length
        updateIndentationLevel(newIndent)
        return result
    }

    private fun updateIndentationLevel(newLevel: Int) {
        val change = abs(newLevel - currentIndentationLevel)
        require(change == 0 || change == INDENTATION_STEP) {
            "Unexpected change of indentation at line ${curLine + 1} from $currentIndentationLevel to $newLevel whitespaces\n" +
                    "Indents are syntactically meaningful in the testdata, " +
                    "please increase/decrease indents only in steps of $INDENTATION_STEP whitespaces"
        }
        currentIndentationLevel = newLevel
    }

    private fun peek(): String = lines[curLine].trim()

    private fun end(): Boolean = curLine >= lines.size

    companion object {
        const val LINE_COMMENT_START_SEPARATOR = "//"
        const val PROJECT_REPORT_START = "MODULES"
        const val TEST_CONFIGURATION_DESCRIPTION_START = "Test configuration:"
        const val INDENTATION_STEP = 4

        fun parse(text: String): ProjectReport {
            val lines = text.trim().lines().dropLastWhile { it.isBlank() }
            return WorkspaceModelTestReportParser(lines).parseProjectReport()
        }
    }
}
