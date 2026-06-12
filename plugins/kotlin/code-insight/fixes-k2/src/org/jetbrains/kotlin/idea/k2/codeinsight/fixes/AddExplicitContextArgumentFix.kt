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

internal class AddExplicitContextArgumentFix(
    element: KtCallElement,
    private val contextParameters: List<ContextParameterFix>
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallElement>(element) {

    sealed interface ContextParameterFix {
        val name: Name

        data class AddArgumentName(override val name: Name, val argumentIndex: Int) : ContextParameterFix
        data class Insert(override val name: Name, val type: String) : ContextParameterFix
    }

    override fun invoke(
        context: ActionContext,
        element: KtCallElement,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(context.project)
        // Get (or create) the argument list. If there's only a trailing lambda, insert an empty `()` before it.
        val argumentList = element.valueArgumentList ?: run {
            val trailingLambda = element.lambdaArguments.firstOrNull() ?: return
            val emptyArgumentList = psiFactory.createCallArguments("()")
            element.addBefore(emptyArgumentList, trailingLambda)
            element.valueArgumentList ?: return
        }

        // 1. Name existing positional arguments (indices are stable: no nodes are added yet).
        val names = contextParameters.filterIsInstance<ContextParameterFix.AddArgumentName>()
        for ((name, argumentIndex) in names) {
            val argument = argumentList.arguments.getOrNull(argumentIndex) ?: continue
            if (argument.getArgumentName() != null) continue
            addArgumentName(argument, name)
        }

        val insertActions = contextParameters.filterIsInstance<ContextParameterFix.Insert>()
        val originalFirstArgument = argumentList.arguments.firstOrNull()
        val anchor = originalFirstArgument ?: argumentList.rightParenthesis
        insertActions.forEachIndexed { index, (name, type) ->
            val newArg = psiFactory.createArgument(
                expression = psiFactory.createExpression("TODO(\"Provide $type\")"),
                name = name,
            )
            argumentList.addBefore(newArg, anchor)

            val isLastInsert = index == insertActions.lastIndex
            if (!isLastInsert || originalFirstArgument != null) {
                argumentList.addBefore(psiFactory.createComma(), anchor)
            }
        }

        argumentList.arguments.firstOrNull()?.getArgumentExpression()?.let { updater.select(it) }
    }

    override fun getActionPresentation(context: ActionContext, element: KtCallElement): Presentation {
        val count = contextParameters.size
        return Presentation.of(KotlinBundle.message("fix.add.explicit.context.argument", count))
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.explicit.context.argument", 1)

}
