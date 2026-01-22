// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddDependencyQuickFixHelper
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.createRenameUnresolvedReferenceFix
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments


/**
 * A provider for quick-fixes related to unresolved references in Kotlin for K2 Mode.
 *
 * Provides [AddDependencyQuickFixHelper] and rename unresolved reference fixes.
 *
 * Import fixes reside in [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFixFactories]
 * and registered via [KotlinK2QuickFixRegistrar].
 */
class KotlinFirUnresolvedReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(reference: PsiReference, registrar: QuickFixActionRegistrar) {
        val ktElement = reference.element as? KtElement ?: return

        for (action in AddDependencyQuickFixHelper.createQuickFix(ktElement)) {
            registrar.register(action)
        }

        if (ktElement is KtNameReferenceExpression) {
            analyze(ktElement) {
                try {
                    createRenameUnresolvedReferenceFix(ktElement)?.let { action -> registrar.register(action) }
                } catch (e: Exception) {
                    if (Logger.shouldRethrow(e)) throw e
                    throw KotlinExceptionWithAttachments("Unable to create rename unresolved reference fix", e)
                        .withPsiAttachment("element.kt", ktElement)
                        .withPsiAttachment("file.kt", ktElement.containingFile)
                }
            }
        }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}