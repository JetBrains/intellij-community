// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class AddExplicitContextArgumentFix(
    element: KtCallElement,
    private val contextParameters: List<ContextParameterFix>
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallElement>(element) {

    sealed interface ContextParameterFix {
        val name: Name
        data class Rename(override val name: Name, val argumentIndex: Int) : ContextParameterFix
        data class Insert(override val name: Name, val type: String) : ContextParameterFix
    }

    override fun invoke(
        context: ActionContext,
        element: KtCallElement,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(context.project)
        val argList = element.valueArgumentList
            ?: (element.lambdaArguments.firstOrNull()?.let {
                element.addBefore(psiFactory.createCallArguments("()"), it) as KtCallElement
            })?.valueArgumentList
            ?: return

        // 1. Rename existing positional arguments (indices are stable: no nodes are added yet).
        for ((name, argumentIndex) in contextParameters.filterIsInstance<ContextParameterFix.Rename>()) {
            argList.arguments.getOrNull(argumentIndex)
                ?.takeIf { it.getArgumentName() == null }
                ?.let { addArgumentName(it, name) }
        }

        // 2. Prepend new explicit arguments.
        val insertActions = contextParameters.filterIsInstance<ContextParameterFix.Insert>()
        for ((name, type) in insertActions) {
            val newArg = psiFactory.createArgument(
                expression = psiFactory.createExpression("TODO(\"Provide $type\")"),
                name = name,
            )
            val anchor = argList.arguments.firstOrNull() ?: argList.rightParenthesis
            if (anchor == argList.rightParenthesis) {
                // Empty arg list: just add the argument; no comma needed.
                argList.addBefore(newArg, anchor)
            } else {
                argList.addBefore(newArg, anchor)
                argList.addBefore(psiFactory.createComma(), anchor)
            }
        }

        argList.arguments.firstOrNull()?.getArgumentExpression()?.let { updater.select(it) }
    }

    private fun createEmptyArgumentList(element: KtCallElement, psiFactory: KtPsiFactory): KtValueArgumentList? {
        val anchor = element.lambdaArguments.firstOrNull() ?: return null
        return element.addBefore(psiFactory.createCallArguments("()"), anchor) as? KtValueArgumentList
    }

    override fun getActionPresentation(context: ActionContext, element: KtCallElement): Presentation {
        val description = contextParameters.joinToString { action ->
            when (action) {
                is ContextParameterFix.Rename -> "${action.name.asString()} = …"
                is ContextParameterFix.Insert -> "${action.name.asString()}: ${action.type}"
            }
        }
        return Presentation.of(KotlinBundle.message("fix.add.explicit.context.argument.detailed", description))
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.explicit.context.argument")
}
