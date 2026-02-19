// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.MixingNamedAndPositionalArguments
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.addArgumentName
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils.getStableNameFor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object AddNameToArgumentFixFactory {
    val addNameToArgumentFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: MixingNamedAndPositionalArguments ->
        val argument = diagnostic.psi.getParentOfType<KtValueArgument>(strict = false) ?: return@ModCommandBased emptyList()
        listOfNotNull(
            getStableNameFor(argument)?.let {
                AddNameToArgumentFix(argument, ElementContext(it))
            }
        )
    }

    private data class ElementContext(
        val argumentName: Name,
    )

    private class AddNameToArgumentFix(
        element: KtValueArgument,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtValueArgument, ElementContext>(element, elementContext) {
        override fun invoke(
            actionContext: ActionContext,
            element: KtValueArgument,
            elementContext: ElementContext,
            updater: ModPsiUpdater
        ) {
            addArgumentName(element, elementContext.argumentName)
        }

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("fix.add.argument.name.family")

        override fun getPresentation(
            context: ActionContext,
            element: KtValueArgument,
        ): Presentation {
            val argumentName = getElementContext(context, element).argumentName
            val text = "${argumentName.identifier} = ${element.text}"
            return Presentation.of(KotlinBundle.message("fix.add.argument.name.text", text))
                .withPriority(PriorityAction.Priority.HIGH)
        }
    }
}