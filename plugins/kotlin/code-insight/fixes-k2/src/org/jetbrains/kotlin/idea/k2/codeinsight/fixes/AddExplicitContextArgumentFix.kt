// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class AddExplicitContextArgumentFix(
    element: KtCallElement,
    private val contextParameters: List<ContextParameterInfo>
) : PsiUpdateModCommandAction<KtCallElement>(element) {

    data class ContextParameterInfo(val name: String, val type: String)

    override fun invoke(
        context: ActionContext,
        element: KtCallElement,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(context.project)
        val argList = element.valueArgumentList ?: createEmptyArgumentList(element, psiFactory) ?: return
        val firstExistingArg = argList.arguments.firstOrNull()

        for (param in contextParameters) {
            val newArgument = psiFactory.createArgument(
                expression = psiFactory.createExpression("TODO(\"Provide ${param.type}\")"),
                name = Name.identifier(param.name),
            )
            if (firstExistingArg != null) {
                argList.addArgumentBefore(newArgument, firstExistingArg)
            } else {
                argList.addArgument(newArgument)
            }
        }
        argList.arguments.firstOrNull()?.getArgumentExpression()?.let { updater.select(it) }
    }

    private fun createEmptyArgumentList(element: KtCallElement, psiFactory: KtPsiFactory): KtValueArgumentList? {
        val anchor = element.lambdaArguments.firstOrNull() ?: return null
        return element.addBefore(psiFactory.createCallArguments("()"), anchor) as? KtValueArgumentList
    }

    override fun getPresentation(context: ActionContext, element: KtCallElement): Presentation {
        val paramDescriptions = contextParameters.joinToString { "${it.name}: ${it.type}" }
        return Presentation.of(KotlinBundle.message("fix.add.explicit.context.argument.detailed", paramDescriptions))
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.explicit.context.argument")
}
