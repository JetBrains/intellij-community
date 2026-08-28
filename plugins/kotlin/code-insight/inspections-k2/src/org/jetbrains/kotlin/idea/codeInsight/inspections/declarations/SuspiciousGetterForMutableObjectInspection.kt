// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.declarations

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.singleExpressionBody
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines.CoroutinesIds
import org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.SuspiciousGetterForMutableObjectInspection.MutableObjectKind
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.canConvertToInitializer
import org.jetbrains.kotlin.idea.codeinsight.utils.convertSingleExpressionGetterToInitializer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val COROUTINE_CALLABLES = listOf(
    CoroutinesIds.Job.factory,
    CoroutinesIds.supervisorJobFactory,
    CoroutinesIds.CompletableDeferred.factory,
    CoroutinesIds.CoroutineScope.factory,
    CoroutinesIds.mainScopeFactory,
    CoroutinesIds.Flows.MutableStateFlow.factory,
    CoroutinesIds.Flows.MutableSharedFlow.factory,
    CoroutinesIds.Channels.Channel.factory,
    CoroutinesIds.Sync.Mutex.factory,
    CoroutinesIds.Sync.Semaphore.factory,
)

private val CALLABLES_TO_CHECK: Map<CallableId, MutableObjectKind> =
    COROUTINE_CALLABLES.associateWith { MutableObjectKind.Coroutines } +
            StandardKotlinNames.Collections.mutableFactories.associateWith { MutableObjectKind.Collections }

internal class SuspiciousGetterForMutableObjectInspection :
    KotlinApplicableInspectionBase<KtPropertyAccessor, SuspiciousGetterForMutableObjectInspection.Context>() {

    private object ReplaceGetterWithInitializerFix : KotlinModCommandQuickFix<KtPropertyAccessor>() {
        override fun applyFix(
            project: Project,
            element: KtPropertyAccessor,
            updater: ModPsiUpdater
        ) {
            element.convertSingleExpressionGetterToInitializer(updater)
        }

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("convert.property.getter.to.initializer")
    }

    internal sealed interface MutableObjectKind {
        fun getDescription(callableId: CallableId): @InspectionMessage String

        context(_: KaSession)
        fun isApplicable(accessor: KtPropertyAccessor, call: KaFunctionCall<*>): Boolean = true

        object Coroutines : MutableObjectKind {
            override fun getDescription(callableId: CallableId) = KotlinBundle.message(
                "inspection.suspicious.getter.for.mutable.object.description.coroutines",
                callableId.callableName,
            )
        }

        object Collections : MutableObjectKind {
            override fun getDescription(callableId: CallableId) = KotlinBundle.message(
                "inspection.suspicious.getter.for.mutable.object.description.collections"
            )

            context(_: KaSession)
            override fun isApplicable(accessor: KtPropertyAccessor, call: KaFunctionCall<*>): Boolean {
                // If we construct a list with some elements, it's more likely possible that the
                // getter is a "template" value that might intentionally get re-created every time.
                if (call.combinedArgumentMapping.isNotEmpty()) return false

                // Overrides often provide default values, so even returning it in a getter can make sense here
                return accessor.property.modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) != true
            }
        }
    }

    override fun isApplicableByPsi(element: KtPropertyAccessor): Boolean {
        return element.isGetter && element.singleExpressionBody() is KtCallExpression
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtPropertyAccessor,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val quickFix = ReplaceGetterWithInitializerFix.takeIf { context.canConvertToInitializer }

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ context.kind.getDescription(context.callableId),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ *listOfNotNull(quickFix).toTypedArray()
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
            visitTargetElement(accessor, holder, isOnTheFly)
        }
    }

    internal class Context(
        val kind: MutableObjectKind,
        val callableId: CallableId,
        val canConvertToInitializer: Boolean,
    )

    context(session: KaSession)
    override fun prepareContext(element: KtPropertyAccessor): Context? {
        val returnedExpression = element.singleExpressionBody() ?: return null
        val call = returnedExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val callableId = call.symbol.callableId ?: return null
        val kind = CALLABLES_TO_CHECK[callableId] ?: return null
        if (!kind.isApplicable(element, call)) return null

        return Context(kind, callableId, canConvertToInitializer = element.canConvertToInitializer())
    }
}
