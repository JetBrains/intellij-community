package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithModality
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
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

        override fun getActionName(
            actionContext: ActionContext,
            element: KtDeclaration,
            elementContext: ElementContext,
        ): String = MakeOverriddenMemberOpenFixUtils.getActionName(element, elementContext.containingDeclarationNames)

        override fun getFamilyName(): String = KotlinBundle.message("add.modifier")
    }
}

context(KaSession)
private fun computeElementContext(element: KtNamedDeclaration): ElementContext? {
    val overriddenNonOverridableMembers = mutableListOf<DeclarationPointer>()
    val containingDeclarationNames = mutableListOf<String>()
    val symbol = element.getSymbol() as? KaCallableSymbol ?: return null

    val allOverriddenSymbols = symbol.getAllOverriddenSymbols()
    for (overriddenSymbol in retainNonOverridableMembers(allOverriddenSymbols)) {
        val overriddenMember = overriddenSymbol.psi
        val containingSymbol = overriddenSymbol.getContainingSymbol()
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

context(KaSession)
private fun retainNonOverridableMembers(
    callableMemberSymbols: Collection<KaCallableSymbol>,
): Collection<KaCallableSymbol> {
    return callableMemberSymbols.filter { !it.isOverridable }
}

private val KaCallableSymbol.isOverridable: Boolean
    get() = (this as? KaSymbolWithModality)?.modality != Modality.FINAL &&
            (this as? KaSymbolWithVisibility)?.visibility != Visibilities.Private &&
            (this.getSymbolContainingMemberDeclarations() as? KaNamedClassOrObjectSymbol)?.isFinalClass != true

private val KaNamedClassOrObjectSymbol.isFinalClass: Boolean
    get() = modality == Modality.FINAL && classKind != KaClassKind.ENUM_CLASS