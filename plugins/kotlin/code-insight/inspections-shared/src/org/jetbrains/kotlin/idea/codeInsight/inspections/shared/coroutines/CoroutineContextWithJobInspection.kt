// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor

internal class CoroutineContextWithJobInspection : KotlinApplicableInspectionBase<KtCallExpression, CoroutineContextWithJobInspection.Context>() {
    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val description = if (context.jobIsCancellable) {
            KotlinBundle.message("inspection.coroutine.context.with.job.description.cancellable", context.usedBuilder.callableName)
        } else {
            KotlinBundle.message("inspection.coroutine.context.with.job.description.non.cancellable", context.usedBuilder.callableName) 
        }
        
        return createProblemDescriptor(
            /* psiElement = */ context.jobSource.element ?: element,
            /* rangeInElement = */ null,
            /* descriptionTemplate = */ description,
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    class Context(val jobSource: SmartPsiElementPointer<KtExpression>, val jobIsCancellable: Boolean, val usedBuilder: CallableId)

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val functionCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null

        val matchedBuilderInfo = SUPPORTED_BUILDERS.find { it.callableId == functionCall.symbol.callableId } ?: return null

        val (coroutineContextArgument, _) = functionCall.argumentMapping.entries
            .find { (_, paramSymbol) -> paramSymbol.returnType.isSubtypeOf(CoroutinesIds.Stdlib.CoroutineContext.ID) }
            ?: return null

        val jobStatus = CoroutineContextJobStatus.detectFor(coroutineContextArgument)

        if (jobStatus !is CoroutineContextJobStatus.WithJob) {
            // we're only interested in the case when there's a job in the context
            return null
        }

        /**
         * We want to warn about detected jobs in two situations:
         * 
         * 1. The job is present and is cancellable. This should be reported for all builders.
         * 2. The job is non-cancellable, but the builder does not support non-cancellable jobs.
         */
        val jobShouldBeReported = jobStatus.isCancellable || !matchedBuilderInfo.allowsNonCancellableJob 

        if (!jobShouldBeReported) return null

        return Context(
            jobSource = jobStatus.source.createSmartPointer(),
            jobIsCancellable = jobStatus.isCancellable,
            usedBuilder = matchedBuilderInfo.callableId,
        )
    }
}

private data class CoroutineBuilderInfo(
    val callableId: CallableId, 
    val allowsNonCancellableJob: Boolean = false,
)

private val SUPPORTED_BUILDERS: Set<CoroutineBuilderInfo> = setOf(
    CoroutineBuilderInfo(CoroutinesIds.launch),
    CoroutineBuilderInfo(CoroutinesIds.async),
    CoroutineBuilderInfo(CoroutinesIds.future),
    CoroutineBuilderInfo(CoroutinesIds.Channels.produce),
    CoroutineBuilderInfo(CoroutinesIds.promise),

    CoroutineBuilderInfo(CoroutinesIds.withContext, allowsNonCancellableJob = true),
    CoroutineBuilderInfo(CoroutinesIds.Flows.flowOn, allowsNonCancellableJob = true),
)
