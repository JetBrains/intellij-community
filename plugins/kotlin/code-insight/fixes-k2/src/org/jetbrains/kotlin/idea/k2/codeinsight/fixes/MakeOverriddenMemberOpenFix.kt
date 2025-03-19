// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getSymbolContainingMemberDeclarations
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.DeclarationPointer
import org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFixUtils
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal object MakeOverriddenMemberOpenFixFactory {
    val makeOverriddenMemberOpenFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.OverridingFinalMember ->
        val declaration = diagnostic.psi
        val elementContext = computeElementContext(declaration) ?: return@ModCommandBased emptyList()
        listOf(MakeOverriddenMemberOpenFix(declaration, elementContext))
    }

    private class MakeOverriddenMemberOpenFix(
        element: KtDeclaration,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtDeclaration, ElementContext>(element, elementContext) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtDeclaration,
            elementContext: ElementContext,
            updater: ModPsiUpdater
        ) {
            val writableMembers = elementContext.overriddenNonOverridableMembers
                .mapNotNull { it.element }
                .map(updater::getWritable)
            MakeOverriddenMemberOpenFixUtils.invoke(writableMembers)
        }

        override fun getPresentation(
            context: ActionContext,
            element: KtDeclaration,
        ): Presentation {
            val (_, containingDeclarationNames) = getElementContext(context, element)
            val actionName = MakeOverriddenMemberOpenFixUtils.getActionName(element, containingDeclarationNames)
            return Presentation.of(actionName)
        }

        override fun getFamilyName(): String =
            KotlinBundle.message("add.modifier")
    }
}

private fun KaSession.computeElementContext(element: KtNamedDeclaration): ElementContext? {
    val overriddenNonOverridableMembers = mutableListOf<DeclarationPointer>()
    val containingDeclarationNames = mutableListOf<String>()
    val symbol = element.symbol as? KaCallableSymbol ?: return null

    val allOverriddenSymbols = symbol.allOverriddenSymbols.toList()
    for (overriddenSymbol in retainNonOverridableMembers(allOverriddenSymbols)) {
        val overriddenMember = overriddenSymbol.psi
        val containingSymbol = overriddenSymbol.containingDeclaration
        if (overriddenMember == null || overriddenMember !is KtCallableDeclaration || !overriddenMember.canRefactorElement() ||
            containingSymbol !is KaNamedSymbol || overriddenMember.modifierList?.hasModifier(KtTokens.OPEN_KEYWORD) == true
        ) {
            return null
        }
        val containingDeclarationName = containingSymbol.name.asString()
        overriddenNonOverridableMembers.add(overriddenMember.createSmartPointer())
        containingDeclarationNames.add(containingDeclarationName)
    }
    return ElementContext(overriddenNonOverridableMembers, containingDeclarationNames)
}

private data class ElementContext(
    val overriddenNonOverridableMembers: List<DeclarationPointer>,
    val containingDeclarationNames: List<String>,
)

private fun retainNonOverridableMembers(
    callableMemberSymbols: Collection<KaCallableSymbol>,
): Collection<KaCallableSymbol> {
    return callableMemberSymbols.filter { !it.isOverridable }
}

private val KaCallableSymbol.isOverridable: Boolean
    get() = modality != KaSymbolModality.FINAL &&
            visibility != KaSymbolVisibility.PRIVATE &&
            (this.getSymbolContainingMemberDeclarations() as? KaNamedClassSymbol)?.isFinalClass != true

private val KaNamedClassSymbol.isFinalClass: Boolean
    get() = modality == KaSymbolModality.FINAL && classKind != KaClassKind.ENUM_CLASS