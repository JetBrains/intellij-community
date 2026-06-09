// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.RemoveArgumentNamesUtils.collectArgumentsContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

internal class RemoveAllArgumentNamesIntention :
    KotlinApplicableModCommandAction<KtCallElement, RemoveAllArgumentNamesIntention.ArgumentsDataContext>(KtCallElement::class) {

    data class ArgumentsDataContext(
        val argumentsThatCanBeUnnamed: List<SmartPsiElementPointer<KtValueArgument>>,
        val vararg: SmartPsiElementPointer<KtValueArgument>?,
        val varargIsArrayOfCall: Boolean,
        val contextArguments: List<Pair<SmartPsiElementPointer<KtValueArgument>, Name>> = emptyList(),
        val removeAll: Boolean,
    )

    override fun getFamilyName(): String = KotlinBundle.message("remove.all.argument.names")

    override fun getActionPresentation(context: ActionContext, element: KtCallElement): Presentation? {
        val elementContext = getElementContext(context, element) ?: return null
        val messageKey = if (elementContext.removeAll) {
            "remove.all.argument.names"
        } else {
            "remove.all.possible.argument.names"
        }
        return Presentation.of(KotlinBundle.message(messageKey))
    }

    override fun isApplicableByPsi(element: KtCallElement): Boolean {
        val arguments = element.valueArgumentList?.arguments ?: return false
        return arguments.count { it.isNamed() } > 1
    }

    override fun KaSession.prepareContext(element: KtCallElement): ArgumentsDataContext? {
        val context = collectArgumentsContext(element) ?: return null
        val argumentsThatCanBeUnnamed = context.argumentsThatCanBeUnnamed
        if (argumentsThatCanBeUnnamed.isEmpty()) return null

        /*
        don't show intention when:
        - no named arguments can be unnamed, i.e., test: varargSpreadNoNamesToRemove.kt
        - only one named argument can be unnamed, because this case will be covered by RemoveSingleArgumentNameIntention
        */
        if (argumentsThatCanBeUnnamed.count { it.isNamed() } < 2) return null

        // don't show intention when red code is a case
        if (context.hasUnmappedArguments) return null

        val totalNamedArgs = element.valueArgumentList?.arguments?.count { it.isNamed() } ?: 0
        val removableCount = argumentsThatCanBeUnnamed.count { it.isNamed() }

        val manager = SmartPointerManager.getInstance(element.project)

        return ArgumentsDataContext(
            argumentsThatCanBeUnnamed = argumentsThatCanBeUnnamed.map(manager::createSmartPsiElementPointer),
            vararg = context.vararg?.let(manager::createSmartPsiElementPointer),
            varargIsArrayOfCall = context.varargIsArrayOfCall,
            contextArguments = context.contextArguments.map { (arg, name) ->
                manager.createSmartPsiElementPointer(arg) to name
            },
            removeAll = removableCount == totalNamedArgs,
        )
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallElement,
      elementContext: ArgumentsDataContext,
      updater: ModPsiUpdater,
    ) {
        val oldArguments = elementContext.argumentsThatCanBeUnnamed.map { it.element ?: return }
        val varargElement = elementContext.vararg?.let { it.element ?: return }
        val contextArguments = elementContext.contextArguments.map { (pointer, name) ->
            (pointer.element ?: return) to name
        }

        val newArguments = oldArguments.flatMap { argument ->
            when (argument) {
                varargElement -> createArgumentWithoutName(argument, isVararg = true, elementContext.varargIsArrayOfCall)
                else -> createArgumentWithoutName(argument)
            }
        }

        val argumentList = element.valueArgumentList ?: return
        oldArguments.forEach { argumentList.removeArgument(it) }

        contextArguments.forEach { (arg, _) -> argumentList.removeArgument(arg) }

        newArguments.asReversed().forEach {
            argumentList.addArgumentBefore(it, argumentList.arguments.firstOrNull())
        }
        
        // Add context arguments at the end with their names
        if (contextArguments.isNotEmpty()) {
            val psiFactory = KtPsiFactory(element.project)
            for ((arg, name) in contextArguments) {
                val exprText = arg.getArgumentExpression()?.text ?: continue
                val namedArg = psiFactory.createArgument(psiFactory.createExpression(exprText), name)
                argumentList.addArgument(namedArg)
            }
        }
    }
}
