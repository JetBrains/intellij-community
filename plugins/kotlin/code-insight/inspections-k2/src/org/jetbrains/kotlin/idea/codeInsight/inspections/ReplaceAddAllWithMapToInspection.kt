// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.runIf
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.psi.getOrCreateValueArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitReceiverInfo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.expressionVisitor

private val addAllName = Name.identifier("addAll")
private val plusAssignName = Name.identifier("plusAssign")
private val mapToName = Name.identifier("mapTo")
private val filterToName = Name.identifier("filterTo")

private val plusAssignCallableId = CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, plusAssignName)
private val addAllCallableId = CallableId(StandardClassIds.MutableCollection, addAllName)
private val mapCallableId = CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("map"))
private val filterCallableId = CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("filter"))

internal class ReplaceAddAllWithMapToInspection : KotlinApplicableInspectionBase.Simple<KtExpression, ReplaceAddAllWithMapToInspection.Context>() {
    class Context(
        /**
         * `true` when it's a `+=` operator call, `false` when it's a regular function call to `addAll`/`plusAssign`.
         */
        val isPlusAssignOperator: Boolean,
        val addAllOperation: Name,
        val replacementOperation: Name,
        val implicitReceiverInfo: ImplicitReceiverInfo?,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        return when (element) {
            is KtBinaryExpression -> element.operationToken == KtTokens.PLUSEQ

            is KtCallExpression -> {
                if (element.parent is KtSafeQualifiedExpression) return false
                val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return false
                calleeExpression.getReferencedNameAsName().let { it == addAllName || it == plusAssignName}
            }

            else -> false
        }
    }

    context(session: KaSession)
    override fun prepareContext(element: KtExpression): Context? {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.symbol

        return when (element) {
            is KtBinaryExpression -> {
                if (!isApplicablePlusAssign(symbol)) return null

                val argument = element.right
                val operation = replacementOperation(argument) ?: return null

                Context(
                    isPlusAssignOperator = true,
                    addAllOperation = plusAssignName,
                    replacementOperation = operation,
                    implicitReceiverInfo = null,
                )
            }

            is KtCallExpression -> {
                val dispatchReceiver = resolvedCall.dispatchReceiver

                val addAllOperation = if (dispatchReceiver != null) {
                    if (!dispatchReceiver.type.isSubtypeOf(StandardClassIds.MutableCollection)) return null
                    if (symbol.allOverriddenSymbolsWithSelf.none { it.callableId == addAllCallableId }) return null
                    addAllName
                } else {
                    if (!isApplicablePlusAssign(symbol)) return null
                    plusAssignName
                }

                val argument = element.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
                val operation = replacementOperation(argument) ?: return null

                Context(
                    isPlusAssignOperator = false,
                    addAllOperation = addAllOperation,
                    replacementOperation = operation,
                    implicitReceiverInfo = runIf(element.parent !is KtDotQualifiedExpression) { element.getImplicitReceiverInfo() }
                )
            }

            else -> null
        }
    }

    context(session: KaSession)
    private fun isApplicablePlusAssign(symbol: KaFunctionSymbol): Boolean {
        if (symbol.callableId != plusAssignCallableId) return false
        if (symbol.receiverType?.classId != StandardClassIds.MutableCollection) return false
        if (symbol.valueParameters.singleOrNull()?.returnType?.classId != StandardClassIds.Iterable) return false
        return true
    }

    context(session: KaSession)
    private fun replacementOperation(argument: KtExpression?): Name? {
        val argumentCall = argument?.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        return when (argumentCall.symbol.callableId) {
            mapCallableId -> mapToName
            filterCallableId -> filterToName
            else -> null
        }
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> {
        return ApplicabilityRange.single(element) {
            when (it) {
                is KtBinaryExpression -> it.operationReference
                is KtCallExpression -> it.calleeExpression
                else -> it
            }
        }
    }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context
    ): @InspectionMessage String {
        return if (context.isPlusAssignOperator) {
            KotlinBundle.message("plus.assign.should.be.replaced.with.map.to", context.replacementOperation)
        } else {
            KotlinBundle.message(
                "add.all.should.be.replaced.with.map.to",
                context.addAllOperation,
                context.replacementOperation
            )
        }
    }

    override fun createQuickFix(
        element: KtExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> = object : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.map.to", context.replacementOperation)

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val factory = KtPsiFactory(project)
            val (receiver, operationCall) = when (element) {
                is KtBinaryExpression -> (element.left?.text ?: return) to (element.right ?: return)
                is KtCallExpression -> {
                    val receiver = buildString {
                        val explicitReceiver = (element.parent as? KtQualifiedExpression)?.receiverExpression?.text
                        if (explicitReceiver != null) {
                            append(explicitReceiver)
                        } else {
                            append("this")
                            context.implicitReceiverInfo?.let {
                                if (!it.isUnambiguousLabel && it.receiverLabel != null) {
                                    append("@")
                                    append(it.receiverLabel)
                                }
                            }
                        }
                    }
                    receiver to (element.valueArguments.singleOrNull()?.getArgumentExpression() ?: return)
                }

                else -> return
            }

            val callExpression = (operationCall as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
                ?: operationCall as? KtCallExpression
                ?: return

            val callee = callExpression.calleeExpression as KtNameReferenceExpression
            callee.replace(factory.createSimpleName(callee.text + "To"))

            val valueArgumentList = callExpression.getOrCreateValueArgumentList()
            valueArgumentList.addArgumentBefore(factory.createArgument(receiver), valueArgumentList.arguments.firstOrNull())

            (element.parent as? KtDotQualifiedExpression ?: element).replace(operationCall)
        }
    }
}
