// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.DataClassResolver

internal class CopyWithoutNamedArgumentsInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, CopyWithoutNamedArgumentsInspection.Context>() {

    data class Context(
        val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor { call ->
        visitTargetElement(call, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val reference = element.referenceExpression() as? KtNameReferenceExpression ?: return false
        return DataClassResolver.isCopy(reference.getReferencedNameAsName()) &&
                element.valueArguments.any { !it.isNamed() }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val receiver = call.partiallyAppliedSymbol.dispatchReceiver?.type?.expandedSymbol as? KaNamedClassSymbol
        if (receiver?.isData != true) return null

        val argumentNames = NamedArgumentUtils.associateArgumentNamesStartingAt(element, startArgument = null)
            ?: return null

        return Context(argumentNames)
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String =
        KotlinBundle.message("copy.method.of.data.class.is.called.without.named.arguments")

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("add.names.to.call.arguments")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val argumentNames = buildMap {
                context.argumentNames.forEach { (pointer, name) ->
                    pointer.element?.let { put(updater.getWritable(it), name) }
                }
            }
            NamedArgumentUtils.addArgumentNames(argumentNames)
        }
    }
}
