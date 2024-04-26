package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
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
    val makeOverriddenMemberOpenFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.OverridingFinalMember ->
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

context(KtAnalysisSession)
private fun computeElementContext(element: KtNamedDeclaration): ElementContext? {
    val overriddenNonOverridableMembers = mutableListOf<DeclarationPointer>()
    val containingDeclarationNames = mutableListOf<String>()
    val symbol = element.getSymbol() as? KtCallableSymbol ?: return null

    val allOverriddenSymbols = symbol.getAllOverriddenSymbols()
    for (overriddenSymbol in retainNonOverridableMembers(allOverriddenSymbols)) {
        val overriddenMember = overriddenSymbol.psi
        val containingSymbol = overriddenSymbol.getContainingSymbol()
        if (overriddenMember == null || overriddenMember !is KtCallableDeclaration || !overriddenMember.canRefactorElement() ||
            containingSymbol !is KtNamedSymbol || overriddenMember.modifierList?.hasModifier(KtTokens.OPEN_KEYWORD) == true
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

context(KtAnalysisSession)
private fun retainNonOverridableMembers(
    callableMemberSymbols: Collection<KtCallableSymbol>,
): Collection<KtCallableSymbol> {
    return callableMemberSymbols.filter { !it.isOverridable }
}

private val KtCallableSymbol.isOverridable: Boolean
    get() = (this as? KtSymbolWithModality)?.modality != Modality.FINAL &&
            (this as? KtSymbolWithVisibility)?.visibility != Visibilities.Private &&
            (this.getSymbolContainingMemberDeclarations() as? KtNamedClassOrObjectSymbol)?.isFinalClass != true

private val KtNamedClassOrObjectSymbol.isFinalClass: Boolean
    get() = modality == Modality.FINAL && classKind != KtClassKind.ENUM_CLASS