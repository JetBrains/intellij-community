// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes2
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddSuspendModifierFixFactory {
    val addSuspendModifierFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IllegalSuspendFunctionCall ->
        val function = (diagnostic.psi as? KtElement)?.containingFunction() ?: return@ModCommandBased emptyList()
        val functionName = function.name ?: return@ModCommandBased emptyList()

        listOf(AddSuspendModifierFix(function, ElementContext(functionName)))
    }

    private data class ElementContext(
        val functionName: String,
    )

    private class AddSuspendModifierFix(
        element: KtModifierListOwner,
        private val context: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtModifierListOwner, ElementContext>(element, context) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtModifierListOwner,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            element.addModifier(KtTokens.SUSPEND_KEYWORD)
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.add.suspend.modifier.function", context.functionName)
    }
}

context(KaSession)
internal fun KtElement.containingFunction(): KtNamedFunction? {
    return when (val containingFunction = getParentOfTypes2<KtFunctionLiteral, KtNamedFunction>()) {
        is KtFunctionLiteral -> {
            val call = containingFunction.getStrictParentOfType<KtCallExpression>()
            val resolvedCall = call?.resolveToCall()?.successfulFunctionCallOrNull()
            if (resolvedCall?.partiallyAppliedSymbol?.symbol?.isInlineOrInsideInline() == true) {
                containingFunction.containingFunction()
            } else {
                null
            }
        }

        is KtNamedFunction -> containingFunction
        else -> null
    }
}

context(KaSession)
private fun KaDeclarationSymbol?.isInlineOrInsideInline(): Boolean = getInlineCallSiteVisibility() != null

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KaDeclarationSymbol?.getInlineCallSiteVisibility(): Visibility? {
    var declaration: KaDeclarationSymbol? = this
    var result: Visibility? = null
    while (declaration != null) {
        if (declaration is KaNamedFunctionSymbol && declaration.isInline) {
            val visibility = declaration.compilerVisibility
            if (Visibilities.isPrivate(visibility)) {
                return visibility
            }
            result = visibility
        }
        declaration = declaration.containingDeclaration
    }
    return result
}
