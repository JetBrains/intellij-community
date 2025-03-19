// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.findParameterWithName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter

internal object AddInlineModifierFixFactories {

    val usageIsNotInlinableFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UsageIsNotInlinable ->
        val reference = diagnostic.psi as? KtNameReferenceExpression ?: return@ModCommandBased emptyList()
        val parameter = reference.findParameterWithName(reference.getReferencedName()) ?: return@ModCommandBased emptyList()
        listOf(AddInlineModifierFix(parameter, ElementContext(KtTokens.NOINLINE_KEYWORD)))
    }

    val nonLocalReturnNotAllowed = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NonLocalReturnNotAllowed ->
        val reference = diagnostic.psi as? KtNameReferenceExpression ?: return@ModCommandBased emptyList()
        val parameter = reference.findParameterWithName(reference.getReferencedName()) ?: return@ModCommandBased emptyList()
        listOf(AddInlineModifierFix(parameter, ElementContext(KtTokens.CROSSINLINE_KEYWORD)))
    }

    val inlineSuspendFunctionTypeUnsupported =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InlineSuspendFunctionTypeUnsupported ->
            val parameter = diagnostic.psi
            listOf(
                AddInlineModifierFix(parameter, ElementContext(KtTokens.NOINLINE_KEYWORD)),
                AddInlineModifierFix(parameter, ElementContext(KtTokens.CROSSINLINE_KEYWORD))
            )
        }

    private data class ElementContext(
        val modifier: KtModifierKeywordToken,
    )

    private class AddInlineModifierFix(
        parameter: KtParameter,
        private val context: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtParameter, ElementContext>(parameter, context) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtParameter,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            element.addModifier(elementContext.modifier)
            if (elementContext.modifier === KtTokens.NOINLINE_KEYWORD) {
                element.removeModifier(KtTokens.CROSSINLINE_KEYWORD)
            }
        }

        override fun getPresentation(
            context: ActionContext,
            element: KtParameter,
        ): Presentation {
            val (modifier) = getElementContext(context, element)
            val actionName = KotlinBundle.message(
                "fix.add.modifier.inline.parameter.text",
                modifier,
                element.name.toString(),
            )
            return Presentation.of(actionName)
        }

        override fun getFamilyName(): String =
            KotlinBundle.message("fix.add.modifier.inline.parameter.family", context.modifier.value)
    }
}
