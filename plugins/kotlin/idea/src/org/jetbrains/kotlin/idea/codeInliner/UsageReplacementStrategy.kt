// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.ModalityUiUtil
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.intentions.ConvertReferenceToLambdaIntention
import org.jetbrains.kotlin.idea.intentions.SpecifyExplicitLambdaSignatureIntention
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.replaceUsages
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun UsageReplacementStrategy.replaceUsagesInWholeProject(
    targetPsiElement: PsiElement,
    @NlsContexts.DialogTitle progressTitle: String,
    @NlsContexts.Command commandName: String,
    unwrapSpecialUsages: Boolean = true,
) {
    val project = targetPsiElement.project
    ProgressManager.getInstance().run(
        object : Task.Modal(project, progressTitle, true) {
            override fun run(indicator: ProgressIndicator) {
                val usages = runReadAction {
                    val searchScope = KotlinSourceFilterScope.projectSources(GlobalSearchScope.projectScope(project), project)
                    ReferencesSearch.search(targetPsiElement, searchScope)
                        .filterIsInstance<KtSimpleReference<KtReferenceExpression>>()
                        .map { ref -> ref.expression }
                }

              ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal()) {
                  project.executeWriteCommand(commandName) {
                      replaceUsages(usages, unwrapSpecialUsages, ::unwrapSpecialUsageOrNull)
                  }
              }
            }
        })
}

fun unwrapSpecialUsageOrNull(
    usage: KtReferenceExpression
): KtSimpleNameExpression? {
    if (usage !is KtSimpleNameExpression) return null

    when (val usageParent = usage.parent) {
        is KtCallableReferenceExpression -> {
            if (usageParent.callableReference != usage) return null
            val (name, descriptor) = usage.nameAndDescriptor
            return ConvertReferenceToLambdaIntention.Holder.applyTo(usageParent)?.let {
                findNewUsage(it, name, descriptor)
            }
        }

        is KtCallElement -> {
            for (valueArgument in usageParent.valueArguments.asReversed()) {
                val callableReferenceExpression = valueArgument.getArgumentExpression() as? KtCallableReferenceExpression ?: continue
                ConvertReferenceToLambdaIntention.Holder.applyTo(callableReferenceExpression)
            }

            val lambdaExpressions = usageParent.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
            if (lambdaExpressions.isEmpty()) return null

            val (name, descriptor) = usage.nameAndDescriptor
            val grandParent = usageParent.parent
            for (lambdaExpression in lambdaExpressions) {
                val functionDescriptor = lambdaExpression.functionLiteral.resolveToDescriptorIfAny() as? FunctionDescriptor ?: continue
                if (functionDescriptor.valueParameters.isNotEmpty()) {
                    SpecifyExplicitLambdaSignatureIntention.Holder.applyTo(lambdaExpression)
                }
            }

            return grandParent.safeAs<KtElement>()?.let {
                findNewUsage(it, name, descriptor)
            }
        }

    }

    return null
}

private val KtSimpleNameExpression.nameAndDescriptor get() = getReferencedName() to resolveToCall()?.candidateDescriptor

private fun findNewUsage(
    element: KtElement,
    targetName: String?,
    targetDescriptor: DeclarationDescriptor?
): KtSimpleNameExpression? = element.findDescendantOfType {
    it.getReferencedName() == targetName && compareDescriptors(it.project, targetDescriptor, it.resolveToCall()?.candidateDescriptor)
}
