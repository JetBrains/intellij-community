// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance

internal class SpecifyTypeExplicitlyInDestructuringAssignmentIntention :
    KotlinApplicableModCommandAction<KtDestructuringDeclaration, SpecifyTypeExplicitlyInDestructuringAssignmentIntention.Context>(
        KtDestructuringDeclaration::class
    ) {

    internal class Context(val entries: List<SmartPsiElementPointer<KtDestructuringDeclarationEntry>>)

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("specify.all.types.explicitly.in.destructuring.declaration")

    override fun isApplicableByPsi(element: KtDestructuringDeclaration): Boolean {
        if (element.containingFile is KtCodeFragment) return false
        val entries = element.entriesWithoutExplicitTypes()
        return entries.isNotEmpty()
    }

    override fun getApplicableRanges(element: KtDestructuringDeclaration): List<TextRange> {
        val endOffset = element.initializer?.let { it.startOffset - 1 } ?: element.endOffset
        return listOf(TextRange(0, endOffset - element.startOffset))
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtDestructuringDeclaration): Context? {
        val entries = element.entriesWithoutExplicitTypes()
        if (entries.any { entry ->
                val symbol = entry.symbol
                symbol.returnType is KaErrorType
            }) return null
        return Context(entries.map { it.createSmartPointer() }.toList())
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDestructuringDeclaration,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val entries = elementContext.entries
        for (entry in entries) {
            val element = entry.element ?: continue
            setTypeForDeclaration(element)
            if (entries.last() == entry) {
                updater.moveCaretTo(element.endOffset)
            }
        }
    }

    private fun setTypeForDeclaration(entry: KtDestructuringDeclarationEntry) {
        val factory = KtPsiFactory(entry.project)
        val typeReference = factory.createType(entry.getKotlinType())
        entry.typeReference = typeReference
    }
}

private fun KtDestructuringDeclaration.entriesWithoutExplicitTypes(): List<KtDestructuringDeclarationEntry> =
    entries.filter { it.typeReference == null }

@OptIn(KaExperimentalApi::class)
private fun KtDestructuringDeclarationEntry.getKotlinType(): String {
    val entry = this
    return analyze(entry) {
        val symbol = entry.symbol
        symbol.returnType.render(renderer = KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE)
    }
}