// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.MultiMap
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.KotlinIdeaAnalysisBundle
import org.jetbrains.kotlin.idea.inspections.KotlinUniversalQuickFix
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AnnotationPresentationInfo(
    val ranges: List<TextRange>,
    @Nls val nonDefaultMessage: String? = null,
    val highlightType: ProblemHighlightType? = null,
    val textAttributes: TextAttributesKey? = null
) {

    companion object {
        private const val KOTLIN_COMPILER_WARNING_ID = "KotlinCompilerWarningOptions"
        private const val KOTLIN_COMPILER_ERROR_ID = "KotlinCompilerErrorOptions"
    }

    fun processDiagnostics(
        holder: HighlightInfoHolder,
        diagnostics: Collection<Diagnostic>,
        highlightInfoBuilderByDiagnostic: MutableMap<Diagnostic, HighlightInfo>? = null,
        highlightInfoByTextRange: MutableMap<TextRange, HighlightInfo>?,
        fixesMap: MultiMap<Diagnostic, IntentionAction>?,
        calculatingInProgress: Boolean
    ) {
        for (range in ranges) {
            for (diagnostic in diagnostics) {
                create(diagnostic, range) { info ->
                    holder.add(info)
                    highlightInfoBuilderByDiagnostic?.put(diagnostic, info)
                    if (fixesMap != null) {
                        applyFixes(fixesMap, diagnostic, info)
                    }
                    if (calculatingInProgress && highlightInfoByTextRange?.containsKey(range) == false) {
                        highlightInfoByTextRange[range] = info
                        QuickFixAction.registerQuickFixAction(info, CalculatingIntentionAction())
                    }
                }
            }
        }
    }

    internal fun applyFixes(
        fixesMap: MultiMap<Diagnostic, IntentionAction>,
        diagnostic: Diagnostic,
        info: HighlightInfo
    ) {
        val warning = diagnostic.severity == Severity.WARNING
        val error = diagnostic.severity == Severity.ERROR
        val fixes = run {
            fixesMap[diagnostic].takeIf { it.isNotEmpty() } ?: if (warning) {
                listOf(CompilerWarningIntentionAction(diagnostic.factory.name))
            } else emptyList()
        }
        val keyForSuppressOptions =
            if (error) {
                HighlightDisplayKey.findOrRegister(
                    KOTLIN_COMPILER_ERROR_ID, KotlinIdeaAnalysisBundle.message("kotlin.compiler.error")
                )
            } else {
                HighlightDisplayKey.findOrRegister(
                    KOTLIN_COMPILER_WARNING_ID, KotlinIdeaAnalysisBundle.message("kotlin.compiler.warning")
                )
            }

        fixes.filter { it is IntentionAction || it is KotlinUniversalQuickFix }.forEach { action ->
            val options = mutableListOf<IntentionAction>()
            action.safeAs<IntentionActionWithOptions>()?.options?.let { options += it }
            info.problemGroup.safeAs<SuppressableProblemGroup>()?.let { group ->
                options += group.getSuppressActions(diagnostic.psiElement).mapNotNull { it as IntentionAction }
            }
            info.registerFix(
                action,
                options,
                KotlinIdeaAnalysisBundle.message(if (error) "kotlin.compiler.error" else "kotlin.compiler.warning"),
                null,
                keyForSuppressOptions
            )
        }
    }

    private fun create(diagnostic: Diagnostic, range: TextRange, consumer: (HighlightInfo) -> Unit) {
        val message = nonDefaultMessage ?: getDefaultMessage(diagnostic)
        HighlightInfo
            .newHighlightInfo(toHighlightInfoType(highlightType, diagnostic.severity))
            .range(range)
            .description(message)
            .escapedToolTip(getMessage(diagnostic))
            .also { builder -> textAttributes?.let(builder::textAttributes) ?: convertSeverityTextAttributes(highlightType, diagnostic.severity)?.let(builder::textAttributes) }
            .also {
                if (diagnostic.severity == Severity.WARNING) {
                    it.problemGroup(KotlinSuppressableWarningProblemGroup(diagnostic.factory))
                }
            }
            .createUnconditionally()
            .also(consumer)
    }

    @NlsContexts.Tooltip
    private fun getMessage(diagnostic: Diagnostic): String {
        var message = IdeErrorMessages.render(diagnostic)
        if (isApplicationInternalMode() || isUnitTestMode()) {
            val factoryName = diagnostic.factory.name
            message = if (message.startsWith("<html>")) {
                @Suppress("HardCodedStringLiteral")
                "<html>[$factoryName] ${message.substring("<html>".length)}"
            } else {
                "[$factoryName] $message"
            }
        }
        if (!message.startsWith("<html>")) {
            message = "<html><body>${XmlStringUtil.escapeString(message)}</body></html>"
        }
        return message
    }

    private fun getDefaultMessage(diagnostic: Diagnostic): String {
        val message = DefaultErrorMessages.render(diagnostic)
        return if (isApplicationInternalMode() || isUnitTestMode()) {
            "[${diagnostic.factory.name}] $message"
        } else {
            message
        }
    }

    private fun toHighlightInfoType(highlightType: ProblemHighlightType?, severity: Severity): HighlightInfoType =
        when (highlightType) {
            ProblemHighlightType.LIKE_UNUSED_SYMBOL -> HighlightInfoType.UNUSED_SYMBOL
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL -> HighlightInfoType.WRONG_REF
            ProblemHighlightType.LIKE_DEPRECATED -> HighlightInfoType.DEPRECATED
            ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL -> HighlightInfoType.MARKED_FOR_REMOVAL
            else -> convertSeverity(highlightType, severity)
        }

    private fun convertSeverity(highlightType: ProblemHighlightType?, severity: Severity): HighlightInfoType =
        when (severity) {
            Severity.ERROR -> HighlightInfoType.ERROR
            Severity.WARNING -> {
                if (highlightType == ProblemHighlightType.WEAK_WARNING) {
                    HighlightInfoType.WEAK_WARNING
                } else HighlightInfoType.WARNING
            }
            Severity.INFO -> HighlightInfoType.WEAK_WARNING
            else -> HighlightInfoType.INFORMATION
        }

    private fun convertSeverityTextAttributes(highlightType: ProblemHighlightType?, severity: Severity): TextAttributesKey? =
        when (highlightType) {
            null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING ->
                when (severity) {
                    Severity.ERROR -> CodeInsightColors.ERRORS_ATTRIBUTES
                    Severity.WARNING -> {
                        if (highlightType == ProblemHighlightType.WEAK_WARNING) {
                            CodeInsightColors.WEAK_WARNING_ATTRIBUTES
                        } else CodeInsightColors.WARNINGS_ATTRIBUTES
                    }
                    Severity.INFO -> CodeInsightColors.WARNINGS_ATTRIBUTES
                    else -> null
                }
            ProblemHighlightType.GENERIC_ERROR -> CodeInsightColors.ERRORS_ATTRIBUTES
            else -> null
        }

}
