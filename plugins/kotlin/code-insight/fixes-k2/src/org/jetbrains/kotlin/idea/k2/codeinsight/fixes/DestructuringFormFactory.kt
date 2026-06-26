// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.buildFullNameBasedDestructuringFormText
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

internal object DestructuringFormFactory {
    val convertToFullFormOnShortFormNameMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.DestructuringShortFormNameMismatch ->
        val entry = diagnostic.psi as? KtDestructuringDeclarationEntry ?: return@ModCommandBased emptyList()
        val declaration = entry.parent as? KtDestructuringDeclaration ?: return@ModCommandBased emptyList()

        val destructuringText = buildFullNameBasedDestructuringFormText(declaration) ?: return@ModCommandBased emptyList()
        listOf(ConvertNameBasedDestructuringToFullFormFix(destructuringText, declaration))
    }

    private class ConvertNameBasedDestructuringToFullFormFix(val destructuringText: String, declaration: KtDestructuringDeclaration) :
        KotlinPsiUpdateModCommandAction.ElementContextless<KtDestructuringDeclaration>(declaration) {
        override fun getFamilyName(): String = KotlinBundle.message("convert.to.full.name.based.form.destructing")
        override operator fun invoke(
            context: ActionContext,
            element: KtDestructuringDeclaration,
            updater: ModPsiUpdater
        ) {
            val psiFactory = KtPsiFactory(context.project)
            val newDestructuringDeclaration = psiFactory.createDestructuringDeclaration(destructuringText)
            element.replace(newDestructuringDeclaration)
        }
    }
}