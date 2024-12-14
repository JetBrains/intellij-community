// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ChangeVisibilityModifierIntention.Internal
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ChangeVisibilityModifierIntention.Private
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ChangeVisibilityModifierIntention.Protected
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ChangeVisibilityModifierIntention.Public
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import kotlin.collections.listOf

class ChangeVisibilityModifierIntentionChooser : PsiBasedModCommandAction<KtDeclaration>(KtDeclaration::class.java) {
    private val modCommands = listOf(Public(), Private(), Protected(), Internal())

    override fun getPresentation(
        context: ActionContext,
        element: KtDeclaration
    ): Presentation? {
        if (modCommands.all { it.getPresentation(context) == null }) return null
        return Presentation.of("${KotlinBundle.message("change.visibility", element.name.toString())}…")
    }

    override fun perform(
        context: ActionContext,
        element: KtDeclaration
    ): ModCommand = ModChooseAction(KotlinBundle.message("change.visibility.popup"), modCommands)

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("change.visibility")
}

sealed class ChangeVisibilityModifierIntention(
    private val modifier: KtModifierKeywordToken,
) : KotlinApplicableModCommandAction<KtDeclaration, Unit>(KtDeclaration::class) {
    class Public : ChangeVisibilityModifierIntention(KtTokens.PUBLIC_KEYWORD) {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean = element.canBePublic() && super.isApplicableByPsi(element)
    }

    class Private : ChangeVisibilityModifierIntention(KtTokens.PRIVATE_KEYWORD) {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean = element.canBePrivate() && super.isApplicableByPsi(element)
    }

    class Protected : ChangeVisibilityModifierIntention(KtTokens.PROTECTED_KEYWORD) {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean = element.canBeProtected() && super.isApplicableByPsi(element)
    }

    class Internal : ChangeVisibilityModifierIntention(KtTokens.INTERNAL_KEYWORD) {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean = element.canBeInternal() && super.isApplicableByPsi(element)
    }

    override fun getPresentation(
        context: ActionContext,
        element: KtDeclaration,
    ): Presentation = Presentation.of(modifier.value)

    override fun getFamilyName(): String = KotlinBundle.message("make.0", modifier.value)

    override fun getApplicableRanges(element: KtDeclaration): List<TextRange> {
        val keywordRange = when (element) {
            is KtNamedFunction -> element.funKeyword?.textRange
            is KtProperty -> element.valOrVarKeyword.textRange
            is KtPropertyAccessor -> element.namePlaceholder.textRange
            is KtClass -> element.getClassOrInterfaceKeyword()?.textRange
            is KtObjectDeclaration -> element.getObjectKeyword()?.textRange
            is KtPrimaryConstructor -> element.getConstructorKeyword()?.textRange
            is KtSecondaryConstructor -> element.getConstructorKeyword().textRange
            is KtParameter -> element.valOrVarKeyword?.textRange
            is KtTypeAlias -> element.getTypeAliasKeyword()?.textRange
            else -> null
        }
        val withoutKeywordRange = when (element) {
            is KtPrimaryConstructor -> element.valueParameterList?.let {
                TextRange.from(it.startOffset, 0) // first position before constructor e.g. Foo<caret>()
            }
            else -> null
        }
        return listOfNotNull(element.modifierList?.range, keywordRange, withoutKeywordRange)
            .map { range -> range.relativeTo(element) }
    }

    override fun isApplicableByPsi(element: KtDeclaration): Boolean {
        val modifierList = element.modifierList
        if (modifierList?.hasModifier(modifier) == true) return false
        if (KtPsiUtil.isLocal((element as? KtPropertyAccessor)?.property ?: element)) return false
        return true
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun prepareContext(element: KtDeclaration): Unit? {
        val symbol = element.symbol

        @OptIn(KaExperimentalApi::class)
        val targetVisibility = modifier.toVisibility()
        if (symbol.compilerVisibility == targetVisibility) return null
        val modifierList = element.modifierList

        if (modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true) {
            val callableDescriptor = symbol as? KaCallableSymbol ?: return null
            // cannot make visibility less than (or non-comparable with) any of the supers
            if (callableDescriptor.allOverriddenSymbols
                    .map { it.compilerVisibility?.compareTo(targetVisibility) }
                    .any { it == null || it > 0 }
            ) return null
        }

        if (element is KtPropertyAccessor) {
            if (element.isGetter) return null
            if (targetVisibility == Visibilities.Public) {
                if (element.modifierList?.visibilityModifierType()?.value == null) return null
            } else {
                val propVisibility = element.property.symbol.compilerVisibility
                if (propVisibility == targetVisibility) return null
                val compare = targetVisibility.compareTo(propVisibility)
                if (compare == null || compare > 0) return null
            }
        }

        return Unit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtDeclaration,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        (element.actualsForExpect() + element.expectDeclarationIfAny() + element).filterNotNull().forEach { declaration ->
            val psiFactory = KtPsiFactory(declaration.project)
            declaration.setVisibility(modifier)
            if (declaration is KtPropertyAccessor) {
                declaration.modifierList?.nextSibling?.replace(psiFactory.createWhiteSpace())
            }
        }
    }
}