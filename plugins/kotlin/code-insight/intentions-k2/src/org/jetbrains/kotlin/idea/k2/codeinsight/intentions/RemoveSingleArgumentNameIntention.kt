// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.RemoveArgumentNamesUtils.collectSortedArgumentsThatCanBeUnnamed
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class RemoveSingleArgumentNameIntention :
    AbstractKotlinModCommandWithContext<KtValueArgument, RemoveSingleArgumentNameIntention.SingleArgumentContext>(KtValueArgument::class) {
    /**
     * @property anchorArgumentPointer an argument after which the unnamed argument should be placed once the argument name is removed;
     * when the argument should be placed in the beginning of argument list, [anchorArgumentPointer] is null
     */
    class SingleArgumentContext(
        val anchorArgumentPointer: SmartPsiElementPointer<KtValueArgument>?,
        val isVararg: Boolean,
        val isArrayOfCall: Boolean,
    )

    override fun getFamilyName(): String = KotlinBundle.message("remove.argument.name")
    override fun getActionName(element: KtValueArgument, context: SingleArgumentContext): String = familyName

    override fun isApplicableByPsi(element: KtValueArgument): Boolean {
        if (!element.isNamed() || element.getArgumentExpression() == null) return false
        return (element.parent as? KtValueArgumentList)?.parent is KtCallElement
    }

    override fun apply(element: KtValueArgument, context: AnalysisActionContext<SingleArgumentContext>, updater: ModPsiUpdater) {
        val argumentList = element.parent as? KtValueArgumentList ?: return

        val analyzeContext = context.analyzeContext
        val newArguments = createArgumentWithoutName(element, analyzeContext.isVararg, analyzeContext.isArrayOfCall)
        argumentList.removeArgument(element)
        newArguments.asReversed().forEach {
            argumentList.addArgumentAfter(it, analyzeContext.anchorArgumentPointer?.element)
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
            anchorArgumentPointer = sortedArgumentsBeforeCurrent.lastOrNull()?.createSmartPointer(),
            isVararg = element == vararg,
            isArrayOfCall = element == vararg && varargIsArrayOfCall
        )
    }
}
