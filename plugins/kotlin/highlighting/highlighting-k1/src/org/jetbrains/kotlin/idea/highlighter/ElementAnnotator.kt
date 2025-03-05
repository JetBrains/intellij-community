// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.psi.MultiRangeReference
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.isCompilerSettingPresent
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtReferenceExpression

internal class ElementAnnotator(
    private val element: PsiElement,
    private val shouldSuppressUnusedParameter: (KtParameter) -> Boolean
) {
    fun registerDiagnosticsAnnotations(
        holder: HighlightInfoHolder,
        diagnostics: Collection<Diagnostic>,
        highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>?,
        calculatingInProgress: Boolean
    ) {
        diagnostics.groupBy { it.factory }
            .forEach {
                val sameTypeDiagnostics = it.value
                val presentationInfo = presentationInfo(sameTypeDiagnostics)
                if (presentationInfo != null) {
                    val fixesMap =
                        if (calculatingInProgress) {
                            Fe10QuickFixProvider.getInstance(element.project)
                                .createPostponedUnresolvedReferencesQuickFixes(sameTypeDiagnostics)
                        } else {
                            createFixesMap(sameTypeDiagnostics)
                        }
                    presentationInfo.processDiagnostics(holder, sameTypeDiagnostics, highlightInfoByDiagnostic, fixesMap, calculatingInProgress)
                }
            }
    }

    private fun presentationInfo(diagnostics: Collection<Diagnostic>): AnnotationPresentationInfo? {
        if (diagnostics.isEmpty() || !diagnostics.any { it.isValid }) return null

        val diagnostic = diagnostics.first()
        // hack till the root cause #KT-21246 is fixed
        if (isUnstableAbiClassDiagnosticForModulesWithEnabledUnstableAbi(diagnostic)) return null

        val factory = diagnostic.factory

        if (suppressDeprecatedAnnotation && factory == Errors.DEPRECATION) return null

        assert(diagnostics.all { it.psiElement == element && it.factory == factory })

        val ranges = diagnostic.textRanges
        val presentationInfo: AnnotationPresentationInfo = when (factory.severity) {
            Severity.ERROR -> {
                when {
                    factory in Errors.UNRESOLVED_REFERENCE_DIAGNOSTICS -> {
                        val referenceExpression = element as KtReferenceExpression
                        val reference = referenceExpression.mainReference
                        if (reference is MultiRangeReference) {
                            AnnotationPresentationInfo(
                                ranges = reference.ranges.map { it.shiftRight(referenceExpression.textOffset) },
                                highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                            )
                        } else {
                            AnnotationPresentationInfo(ranges, highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                        }
                    }

                    factory == Errors.ILLEGAL_ESCAPE -> AnnotationPresentationInfo(
                        ranges, textAttributes = KotlinHighlightingColors.INVALID_STRING_ESCAPE
                    )

                    factory == Errors.REDECLARATION -> AnnotationPresentationInfo(
                        ranges = listOf(diagnostic.textRanges.first()), nonDefaultMessage = ""
                    )

                    factory == Errors.UNSUPPORTED_FEATURE && diagnostic.unsupportedFeatureOrNull() in UNSUPPORTED_K2_BETA_FEATURES ->
                        AnnotationPresentationInfo(
                            ranges, nonDefaultMessage = KotlinBaseFe10HighlightingBundle.message(
                                "the.feature.0.is.not.supported.in.k1.mode",
                                diagnostic.unsupportedFeatureOrNull()?.quotedPresentableName().orEmpty(),
                            )
                        )

                    else -> {
                        AnnotationPresentationInfo(
                            ranges,
                            highlightType =
                            when (factory) {
                                Errors.INVISIBLE_REFERENCE, Errors.DELEGATE_SPECIAL_FUNCTION_MISSING, Errors.DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE, Errors.TOO_MANY_ARGUMENTS -> ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                                else -> null
                            },
                            textAttributes =
                            when (factory) {
                                Errors.DELEGATE_SPECIAL_FUNCTION_MISSING, Errors.DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE, Errors.TOO_MANY_ARGUMENTS -> CodeInsightColors.ERRORS_ATTRIBUTES
                                else -> null
                            },
                        )
                    }
                }
            }

            Severity.WARNING -> {
                if (factory == Errors.UNUSED_PARAMETER && shouldSuppressUnusedParameter(element as KtParameter)) {
                    return null
                }

                AnnotationPresentationInfo(
                    ranges,
                    textAttributes = when (factory) {
                        Errors.DEPRECATION -> CodeInsightColors.DEPRECATED_ATTRIBUTES
                        Errors.UNUSED_ANONYMOUS_PARAMETER -> CodeInsightColors.WEAK_WARNING_ATTRIBUTES
                        else -> null
                    },
                    highlightType = when (factory) {
                        in Errors.UNUSED_ELEMENT_DIAGNOSTICS, Errors.UNUSED_DESTRUCTURED_PARAMETER_ENTRY ->
                            ProblemHighlightType.LIKE_UNUSED_SYMBOL

                        Errors.UNUSED_ANONYMOUS_PARAMETER -> ProblemHighlightType.WEAK_WARNING
                        else -> null
                    }
                )
            }

            Severity.INFO -> AnnotationPresentationInfo(ranges, highlightType = ProblemHighlightType.INFORMATION)
        }
        return presentationInfo
    }

    private fun createFixesMap(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction> =
        try {
            Fe10QuickFixProvider.getInstance(element.project).createQuickFixes(sameTypeDiagnostics)
        } catch (e: Exception) {
            if (e is ControlFlowException) {
                throw e
            }
            LOG.error(e)
            MultiMap()
        }

    private fun isUnstableAbiClassDiagnosticForModulesWithEnabledUnstableAbi(diagnostic: Diagnostic): Boolean {
        val factory = diagnostic.factory
        if (factory != Errors.IR_WITH_UNSTABLE_ABI_COMPILED_CLASS && factory != Errors.FIR_COMPILED_CLASS) return false

        val module = element.module ?: return false
        val moduleFacetSettings = KotlinFacetSettingsProvider.getInstance(element.project)?.getSettings(module) ?: return false
        return when (factory) {
            Errors.IR_WITH_UNSTABLE_ABI_COMPILED_CLASS ->
                true

            Errors.FIR_COMPILED_CLASS ->
                moduleFacetSettings.isCompilerSettingPresent(K2JVMCompilerArguments::useK2)

            else -> error(factory)
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(ElementAnnotator::class.java)

        internal val suppressDeprecatedAnnotationRegistryKey: RegistryValue = Registry.get("kotlin.highlighting.suppress.deprecated")
        @Volatile
        private var suppressDeprecatedAnnotation: Boolean = false

        internal fun updateSuppressDeprecatedAnnotationValue() {
            suppressDeprecatedAnnotation = suppressDeprecatedAnnotationRegistryKey.asBoolean()
        }
    }
}
