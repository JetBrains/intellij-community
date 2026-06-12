// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class AddContextParameterFix(
    element: KtElement,
    private val contextParameters: ContextParameter,
    private val targetFunctionPointer: SmartPsiElementPointer<KtNamedFunction>? = null,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtElement>(element) {

    /** Parameter to add. [name] = `null` produces an anonymous `_: Type` entry. */
    data class ContextParameter(val name: Name?, val type: String) {
        fun render(): String = "${name?.render() ?: "_"}: $type"
    }

    override fun invoke(context: ActionContext, element: KtElement, updater: ModPsiUpdater) {
        val targetFunction = targetFunctionPointer?.element?.let(updater::getWritable)
            ?: element.getStrictParentOfType<KtNamedFunction>()
            ?: return

        val psiFactory = KtPsiFactory(context.project)
        val existingParameters = targetFunction.contextParameters
        val existingText = existingParameters.joinToString(", ") { it.text }
        val newParam = "_: ${contextParameters.type}"
        val contextClause = if (existingText.isEmpty()) "context($newParam)" else "context($existingText, $newParam)"

        val newFunctionText = if (existingParameters.isNotEmpty()) {
            val oldContextEnd =
                existingParameters.last().parent.textRange.endOffset - targetFunction.textRange.startOffset
            val rest = targetFunction.text.substring(oldContextEnd).trimStart()
            "$contextClause $rest"
        } else {
            "$contextClause\n${targetFunction.text}"
        }

        val newFunction = psiFactory.createFunction(newFunctionText)
        val replacedFunction = targetFunction.replace(newFunction) as KtNamedFunction

        if (targetFunctionPointer == null) {
            val newParam = replacedFunction.contextParameters.getOrNull(existingParameters.size) ?: return
            updater.select(newParam.nameIdentifier ?: return)
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.context.parameter.family")

}