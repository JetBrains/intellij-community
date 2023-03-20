// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.RemoveArgumentNamesUtils.collectSortedArgumentsThatCanBeUnnamed
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class RemoveSingleArgumentNameIntention :
    AbstractKotlinApplicableIntentionWithContext<KtValueArgument, RemoveSingleArgumentNameIntention.SingleArgumentContext>(KtValueArgument::class) {
    /**
     * @property anchorArgument an argument after which the unnamed argument should be placed once the argument name is removed;
     * when the argument should be placed in the beginning of argument list, [anchorArgument] is null
     */
    class SingleArgumentContext(
        val anchorArgument: KtValueArgument?,
        val isVararg: Boolean,
        val isArrayOfCall: Boolean
    )

    override fun getFamilyName(): String = KotlinBundle.message("remove.argument.name")
    override fun getActionName(element: KtValueArgument, context: SingleArgumentContext): String = familyName

    override fun isApplicableByPsi(element: KtValueArgument): Boolean {
        if (!element.isNamed() || element.getArgumentExpression() == null) return false
        return (element.parent as? KtValueArgumentList)?.parent is KtCallElement
    }

    override fun apply(element: KtValueArgument, context: SingleArgumentContext, project: Project, editor: Editor?) {
        val argumentList = element.parent as? KtValueArgumentList ?: return

        val newArguments = createArgumentWithoutName(element, context.isVararg, context.isArrayOfCall)
        argumentList.removeArgument(element)
        newArguments.asReversed().forEach {
            argumentList.addArgumentAfter(it, context.anchorArgument)
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgument> = applicabilityRange { valueArgument ->
        val argumentExpression = valueArgument.getArgumentExpression() ?: return@applicabilityRange null
        TextRange(valueArgument.startOffset, argumentExpression.startOffset).relativeTo(valueArgument)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtValueArgument): SingleArgumentContext? {
        val callElement = element.getStrictParentOfType<KtCallElement>() ?: return null
        val (sortedArguments, vararg, varargIsArrayOfCall) = collectSortedArgumentsThatCanBeUnnamed(callElement) ?: return null
        if (element !in sortedArguments) return null

        val allArguments = callElement.valueArgumentList?.arguments ?: return null
        val sortedArgumentsBeforeCurrent = sortedArguments.takeWhile { it != element }

        val supportsMixed = element.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)
        val nameCannotBeRemoved = if (supportsMixed) {
            sortedArgumentsBeforeCurrent.withIndex().any { (parameterIndex, argument) -> parameterIndex != allArguments.indexOf(argument) }
        } else {
            sortedArgumentsBeforeCurrent.any { it.isNamed() }
        }
        if (nameCannotBeRemoved) return null

        return SingleArgumentContext(
            anchorArgument = sortedArgumentsBeforeCurrent.lastOrNull(),
            isVararg = element == vararg,
            isArrayOfCall = element == vararg && varargIsArrayOfCall
        )
    }
}
