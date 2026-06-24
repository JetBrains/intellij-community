// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

sealed class AddContextParameterFix(
    element: KtElement,
    private val contextParameter: ContextParameter,
    private val targetFunctionPointer: SmartPsiElementPointer<KtNamedFunction>? = null,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtElement>(element) {

    /** Parameter to add. [name] = `null` produces an anonymous `_: Type` entry. */
    data class ContextParameter(val name: Name?, val type: String) {
        fun render(): String = "${name?.render() ?: "_"}: $type"
    }

    protected abstract fun targetFunction(element: KtElement, updater: ModPsiUpdater): KtNamedFunction?

    /** Whether to move/select the caret after modification. Useful for in-scope edits. */
    protected open val updatesCaret: Boolean get() = true

    override fun invoke(context: ActionContext, element: KtElement, updater: ModPsiUpdater) {
        val targetFunction = targetFunction(element, updater) ?: return

        val psiFactory = KtPsiFactory(context.project)
        val contextClause = targetFunction.modifierList?.contextParameterList

        val addedParameter: KtParameter = if (contextClause != null) {
            val rParen = contextClause.node.findChildByType(KtTokens.RPAR)?.psi ?: return
            val hasTrailingComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(rParen)
                ?.node?.elementType == KtTokens.COMMA
            if (!hasTrailingComma) {
                contextClause.addBefore(psiFactory.createComma(), rParen)
            }
            contextClause.addBefore(psiFactory.createParameter(contextParameter.render()), rParen) as KtParameter
        } else {
            val template = psiFactory.createFunction(
                "context(${contextParameter.render()})\nfun stub() {}"
            )
            val funKeyword = targetFunction.funKeyword ?: return
            val targetModifierList = targetFunction.modifierList
            val templateModifierList = template.modifierList ?: return
            val inserted = if (targetModifierList == null) {
                (targetFunction.addBefore(templateModifierList, funKeyword) as? KtModifierList)?.contextParameterList
            } else {
                val newContextClause = templateModifierList.contextParameterList ?: return
                val visibilityModifier = targetModifierList.visibilityModifier()
                if (visibilityModifier != null) {
                    targetModifierList.addBefore( newContextClause, visibilityModifier)
                } else {
                    targetModifierList.add(newContextClause)
                } as? KtContextParameterList
            } ?: return
            inserted.contextParameters.firstOrNull() ?: return
        }
        shortenReferences(addedParameter)
        if (updatesCaret) {
            updater.select(addedParameter.nameIdentifier ?: return)
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.context.parameter.family")

    class ForEnclosingFunction(
        element: KtElement,
        contextParameter: ContextParameter,
    ) : AddContextParameterFix(element, contextParameter) {

        override fun targetFunction(element: KtElement, updater: ModPsiUpdater): KtNamedFunction? =
            element.getStrictParentOfType<KtNamedFunction>()
    }

    /** Adds context parameters to a specific [targetFunctionPointer].
     *  The target is in another location.*/
    class ForCalledFunction(
        element: KtElement,
        contextParameter: ContextParameter,
        private val targetFunctionPointer: SmartPsiElementPointer<KtNamedFunction>,
    ) : AddContextParameterFix(element, contextParameter) {

        override val updatesCaret: Boolean get() = false

        override fun targetFunction(element: KtElement, updater: ModPsiUpdater): KtNamedFunction? =
            targetFunctionPointer.element?.let(updater::getWritable)
    }
}