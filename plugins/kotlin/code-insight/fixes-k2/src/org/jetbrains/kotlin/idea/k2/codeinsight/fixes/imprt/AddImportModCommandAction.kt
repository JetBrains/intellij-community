// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtElement

/**
 * A [ModCommand]-based analog of [ImportQuickFix].
 *
 * Provides a selection of [importVariants] by using
 * a [com.intellij.modcommand.ModChooseAction].
 *
 * Currently, it's not supposed to be used in IntelliJ IDEA,
 * and should only be used in the Kotlin LSP Server setup.
 *
 * Note: Avoid instantiating directly, see [KotlinAddImportActionFactory] for that.
 */
internal class AddImportModCommandAction(
    element: KtElement,
    private val intentionText: @IntentionName String,
    private val importVariants: List<AutoImportVariant>,
) : PsiBasedModCommandAction<KtElement>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.import")

    override fun getPresentation(context: ActionContext, element: KtElement): Presentation = Presentation.of(intentionText)

    override fun perform(context: ActionContext, element: KtElement): ModCommand {
        val individualImportActions = importVariants.map { variant ->
            AddImportVariantModCommandAction(element, variant)
        }
        return ModCommand.chooseAction(KotlinBundle.message("fix.import"), individualImportActions)
    }
}

private class AddImportVariantModCommandAction(
    element: KtElement,
    private val importVariant: AutoImportVariant,
) : PsiUpdateModCommandAction<KtElement>(element) {

    override fun getFamilyName(): String = importVariant.hint

    override fun invoke(context: ActionContext, element: KtElement, updater: ModPsiUpdater) {
        require(importVariant is SymbolBasedAutoImportVariant)

        val file = element.containingKtFile

        val useShortening = analyze(element) {
            ImportQuickFix.shouldBeImportedWithShortening(element, importVariant)
        }

        if (useShortening) {
            (element.mainReference as? KtSimpleNameReference)?.bindToFqName(
                importVariant.fqName,
                KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING,
            )
        } else {
            file.addImport(importVariant.fqName)
        }
    }
}
