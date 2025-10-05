// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddDependencyQuickFixHelper
import org.jetbrains.kotlin.idea.highlighter.binaryExpressionForOperationReference
import org.jetbrains.kotlin.idea.highlighter.restoreKaDiagnosticsForUnresolvedReference
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFixProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.createRenameUnresolvedReferenceFix
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments


/**
 * A provider for quick-fixes related to unresolved references in Kotlin for K2 Mode.
 *
 * Used as a lazy alternative to registering factories in [KotlinK2QuickFixRegistrar] to
 * postpone some work during the highlighting (see KTIJ-26874).
 *
 * Triggered from [org.jetbrains.kotlin.idea.highlighting.visitor.KotlinDiagnosticHighlightVisitor].
 */
class KotlinFirUnresolvedReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<PsiReference>() {
    companion object {
        private val LOG = Logger.getInstance(KotlinFirUnresolvedReferenceQuickFixProvider::class.java)
    }

    override fun registerFixes(reference: PsiReference, registrar: QuickFixActionRegistrar) {
        val ktElement = reference.element as? KtElement ?: return

        for (action in AddDependencyQuickFixHelper.createQuickFix(ktElement)) {
            registrar.register(action)
        }

        analyze(ktElement) {
            val savedDiagnostics = ktElement.restoreKaDiagnosticsForUnresolvedReference()
                .ifEmpty {
                    // if no diagnostics on the original element, 
                    // try to backtrack to the binary expression parent (see KT-75331)
                    ktElement.binaryExpressionForOperationReference?.restoreKaDiagnosticsForUnresolvedReference()
                }
                .orEmpty()

            LOG.debug {
                savedDiagnostics.joinToString(prefix = "unresolved references diagnostics for file=${ktElement.containingFile.virtualFile.path}:\n", separator = "\n") {
                    "${it.defaultMessage}; textRanges=${it.textRanges}"
                }
            }

            for (quickFix in ImportQuickFixProvider.getFixes(savedDiagnostics)) {
                registrar.register(quickFix)
            }

            if (ktElement is KtNameReferenceExpression) {
                try {
                    createRenameUnresolvedReferenceFix(ktElement)?.let { action -> registrar.register(action) }
                } catch (e: Exception) {
                    if (e is ControlFlowException) throw e
                    throw KotlinExceptionWithAttachments("Unable to create rename unresolved reference fix", e)
                        .withPsiAttachment("element.kt", ktElement)
                        .withPsiAttachment("file.kt", ktElement.containingFile)
                }
            }
        }
    }



    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}