// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.RemoveArgumentNamesUtils.collectSortedArgumentsThatCanBeUnnamed
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument

internal class RemoveAllArgumentNamesIntention :
    KotlinApplicableModCommandAction<KtCallElement, RemoveAllArgumentNamesIntention.ArgumentsDataContext>(KtCallElement::class) {

    data class ArgumentsDataContext(
        val sortedArguments: List<SmartPsiElementPointer<KtValueArgument>>,
        val vararg: SmartPsiElementPointer<KtValueArgument>?,
        val varargIsArrayOfCall: Boolean,
    )

    override fun getFamilyName(): String = KotlinBundle.message("remove.all.argument.names")

    override fun isApplicableByPsi(element: KtCallElement): Boolean {
        val arguments = element.valueArgumentList?.arguments ?: return false
        return arguments.count { it.isNamed() } > 1
    }

    override fun KaSession.prepareContext(element: KtCallElement): ArgumentsDataContext? {
        val context = collectSortedArgumentsThatCanBeUnnamed(element) ?: return null
        if (context.sortedArguments.isEmpty()) return null
        val manager = SmartPointerManager.getInstance(element.project)
        return ArgumentsDataContext(
            sortedArguments = context.sortedArguments.map(manager::createSmartPsiElementPointer),
            vararg = context.vararg?.let(manager::createSmartPsiElementPointer),
            varargIsArrayOfCall = context.varargIsArrayOfCall,
        )
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallElement,
      elementContext: ArgumentsDataContext,
      updater: ModPsiUpdater,
    ) {
        val oldArguments = elementContext.sortedArguments.map { it.element ?: return }
        val varargElement = elementContext.vararg?.let { it.element ?: return }

        val newArguments = oldArguments.flatMap { argument ->
            when (argument) {
                varargElement -> createArgumentWithoutName(argument, isVararg = true, elementContext.varargIsArrayOfCall)
                else -> createArgumentWithoutName(argument)
            }
        }

        val argumentList = element.valueArgumentList ?: return
        oldArguments.forEach { argumentList.removeArgument(it) }

        newArguments.asReversed().forEach {
            argumentList.addArgumentBefore(it, argumentList.arguments.firstOrNull())
        }
    }
}
