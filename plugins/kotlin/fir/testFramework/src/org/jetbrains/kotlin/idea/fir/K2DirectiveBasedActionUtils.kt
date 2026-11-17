// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.AFTER_ERROR_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.AFTER_WARNING_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.DISABLE_ERRORS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.ENABLE_WARNINGS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.ERROR_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.WARNING_DIRECTIVE
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

object K2DirectiveBasedActionUtils {
    const val DISABLE_K2_ERRORS_DIRECTIVE: String = "// DISABLE_K2_ERRORS"

    const val K2_ERROR_DIRECTIVE: String = "// K2_ERROR:"
    const val K2_AFTER_ERROR_DIRECTIVE: String = "// K2_AFTER_ERROR:"

    const val SYNTAX_ERROR_NAME: String = "SYNTAX"

    private fun isErrorChecksSuppressed(fileText: String): Boolean =
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, DISABLE_ERRORS_DIRECTIVE, DISABLE_K2_ERRORS_DIRECTIVE).isNotEmpty()

    private fun isWarningChecksEnabled(fileText: String): Boolean =
        InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, ENABLE_WARNINGS_DIRECTIVE).isNotEmpty()

    fun checkForUnexpectedErrors(
        mainFile: File,
        ktFile: KtFile,
        fileText: String,
        vararg directives: String = arrayOf(K2_ERROR_DIRECTIVE, ERROR_DIRECTIVE)
    ) {
        if (isErrorChecksSuppressed(fileText)) return

        checkForUnexpected(mainFile, ktFile, fileText, "errors", KaSeverity.ERROR, *directives)
    }

    /**
     * warnings check is disabled by default
     */
    fun checkForWarningsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        if (!isWarningChecksEnabled(fileText)) return

        checkForUnexpected(mainFile, ktFile, fileText, "warnings", KaSeverity.WARNING, WARNING_DIRECTIVE)
    }

    fun checkForErrorsBefore(mainFile: File, ktFile: KtFile, fileText: String) {
        if (isErrorChecksSuppressed(fileText)) return

        checkForUnexpected(mainFile, ktFile, fileText, "errors", KaSeverity.ERROR, K2_ERROR_DIRECTIVE, ERROR_DIRECTIVE)
    }

    fun checkForWarningsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        if (!isWarningChecksEnabled(fileText)) return

        checkForUnexpected(mainFile, ktFile, fileText, "warnings", KaSeverity.WARNING, AFTER_WARNING_DIRECTIVE)
    }

    fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        if (isErrorChecksSuppressed(fileText)) return

        checkForUnexpected(mainFile, ktFile, fileText, "errors", KaSeverity.ERROR, K2_AFTER_ERROR_DIRECTIVE, AFTER_ERROR_DIRECTIVE)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun checkForUnexpected(
        mainFile: File,
        file: KtFile,
        fileText: String,
        name: String,
        severity: KaSeverity,
        vararg directives: String,
    ) {
        val firstDirective = directives.first()
        val (directive, lines) = directives.firstNotNullOfOrNull { directive ->
            val lines = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, directive)
            lines.takeIf { it.isNotEmpty() }?.let { directive to it }
        } ?: (firstDirective to emptyList())

        val expected = lines
            .filter { it.isNotBlank() }
            .sorted()
            .map { "$directive $it" }

        val diagnosticNames =
            allowAnalysisOnEdt {
                analyze(file) {
                    val diagnostics =
                        // filter level has to be consistent with
                        // [org.jetbrains.kotlin.idea.highlighting.visitor.KotlinDiagnosticHighlightVisitor#analyzeFile]
                        file.diagnostics()
                    buildList {
                        val names = diagnostics
                            .filter { it.severity == severity }
                            .map { it.factoryName }
                        addAll(names)
                        // We don't want to report FIR SYNTAX errors twice, but some syntax errors are not reported by FIR
                        if (severity == KaSeverity.ERROR
                            && SYNTAX_ERROR_NAME !in names
                            && PsiTreeUtil.hasErrorElements(file)
                        ) {
                            add(SYNTAX_ERROR_NAME)
                        }
                    }
                }
            }
        val actual = diagnosticNames
            .map { "$directive $it" }
            .sorted()

        if (actual == expected) return

        val lastDirective = directives.last()
        val trimmedActual = fileText.replace(Regex("${firstDirective}.*\n"), "")

        var firstNotCommentedLineIndex = 0
        for (line in trimmedActual.lines()) {
            if (!line.startsWith("//")) break
            firstNotCommentedLineIndex += line.length + 1 // as `\n`
        }
        val actualString = trimmedActual.substring(0, firstNotCommentedLineIndex) +
                (actual.takeIf { it.isNotEmpty() } ?: listOf(firstDirective)).joinToString(separator = "\n", postfix = "\n") { it.replace(lastDirective, firstDirective) } +
                trimmedActual.substring(firstNotCommentedLineIndex)

        throw FileComparisonFailedError(
            "All actual $name should be mentioned in test data with '$directive' directive. " +
                    "But no unnecessary $name should be mentioned\n" +
                    "actual errors:\n$actualString\n" +
                    "file:\n$fileText",
            expected = fileText,
            actual = actualString,
            expectedFilePath = mainFile.absolutePath,
        )
    }
}