// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.RemoveArgumentNamesUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.RemoveArgumentNamesUtils.collectSortedArgumentsThatCanBeUnnamed
import org.jetbrains.kotlin.psi.KtCallElement

internal class RemoveAllArgumentNamesIntention :
    AbstractKotlinApplicableIntentionWithContext<KtCallElement, RemoveArgumentNamesUtils.ArgumentsData>(KtCallElement::class) {

    override fun getFamilyName(): String = KotlinBundle.message("remove.all.argument.names")
    override fun getActionName(element: KtCallElement, context: RemoveArgumentNamesUtils.ArgumentsData): String = familyName

    override fun isApplicableByPsi(element: KtCallElement): Boolean {
        val arguments = element.valueArgumentList?.arguments ?: return false
        return arguments.count { it.isNamed() } > 1
    }

    override fun apply(element: KtCallElement, context: RemoveArgumentNamesUtils.ArgumentsData, project: Project, editor: Editor?) {
        val newArguments = context.sortedArguments.flatMap { argument ->
            when (argument) {
                context.vararg -> createArgumentWithoutName(argument, isVararg = true, context.varargIsArrayOfCall)
                else -> createArgumentWithoutName(argument)
            }
        }

        val argumentList = element.valueArgumentList ?: return
        context.sortedArguments.forEach { argumentList.removeArgument(it) }

        newArguments.asReversed().forEach {
            argumentList.addArgumentBefore(it, argumentList.arguments.firstOrNull())
        }
    }

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallElement): RemoveArgumentNamesUtils.ArgumentsData? {
        val context = collectSortedArgumentsThatCanBeUnnamed(element) ?: return null
        if (context.sortedArguments.isEmpty()) return null
        return context
    }
}