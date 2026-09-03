// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.expressions.expectedType
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.allSupertypes
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines.SuspiciousMutableCollectionInStateFlowInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.qualifiedCalleeExpressionTextRangeInThis
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

internal class SuspiciousMutableCollectionInStateFlowInspection : KotlinApplicableInspectionBase<KtCallExpression, Context>() {

    internal class Context(val readOnlyCallableId: CallableId?) {
        fun createQuickFixes(): List<KotlinModCommandQuickFix<KtValueArgument>> {
            if (readOnlyCallableId == null) return emptyList()
            return listOf(UseReadOnlyCollectionQuickFix(readOnlyCallableId))
        }
    }

    private class UseReadOnlyCollectionQuickFix(
        private val readOnlyCallableId: CallableId
    ) : KotlinModCommandQuickFix<KtValueArgument>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.suspicious.mutable.collection.in.state.flow.fix")

        override fun applyFix(
            project: Project,
            element: KtValueArgument,
            updater: ModPsiUpdater
        ) {
            val callExpression = element.getArgumentExpression() as? KtCallExpression ?: return

            val callFqName = readOnlyCallableId.asSingleFqName().withRootPrefixIfNeeded(callExpression)
            val replacementCallText = callExpression.qualifiedCalleeExpressionTextRangeInThis
                ?.replace(callExpression.text, callFqName.asString())
                ?: return

            val replacementCall = KtPsiFactory(project).createExpression(replacementCallText)
            val replacedExpression = callExpression.replace(replacementCall)
            if (replacedExpression is KtElement) {
                ShortenReferencesFacility.getInstance().shorten(replacedExpression)
            }
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element.valueArguments.first(),
        /* rangeInElement = */ null,
        /* descriptionTemplate = */ KotlinBundle.message("inspection.suspicious.mutable.collection.in.state.flow.description"),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
        /* ...fixes = */ *context.createQuickFixes().toTypedArray()
    )

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.valueArguments.firstOrNull()?.getArgumentExpression() == null) return false
        val name = element.calleeExpression?.text ?: return false
        val stateFlowName = CoroutinesIds.Flows.MutableStateFlow.ID.shortClassName.asString()
        if (name == stateFlowName) return true

        val importAlias = element.containingKtFile.findAliasByFqName(CoroutinesIds.Flows.MutableStateFlow.ID.asSingleFqName())
        return importAlias != null && importAlias.name == name
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    context(session: KaSession)
    private fun KtCallExpression.getAssociatedReadOnlyCallableId(): CallableId? {
        // Note: we only support non-aliased collection factories here for providing the quick-fix
        val calleeExpression = calleeExpression as? KtNameReferenceExpression ?: return null
        val calleeName = calleeExpression.text
        if (!calleeName.startsWith("mutable")) return null

        val call = calleeExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val callableId = call.symbol.callableId ?: return null
        val isMutableFactory = callableId in StandardKotlinNames.Collections.mutableFactories
        if (!isMutableFactory) return null

        val readOnlyFactoryName = calleeName.removePrefix("mutable").decapitalizeAsciiOnly()
        val newCallable = session.findTopLevelCallables(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier(readOnlyFactoryName))
            .firstOrNull()
        if (newCallable == null) return null

        return newCallable.callableId
    }

    private val mutableTypesToCheck = setOf(
        StandardClassIds.MutableCollection,
        StandardClassIds.MutableMap,
    )

    context(session: KaSession)
    override fun prepareContext(element: KtCallExpression): Context? {
        val expressionType = element.expressionType as? KaClassType ?: return null
        if (expressionType.classId != CoroutinesIds.Flows.MutableStateFlow.ID) return null
        val containingType = expressionType.typeArguments.singleOrNull()?.type as? KaClassType ?: return null

        val superTypesAndSelf = sequenceOf(containingType) + containingType.allSupertypes
        if (superTypesAndSelf.none { it is KaClassType && it.classId in mutableTypesToCheck }) return null
        val collectionArgument = element.valueArguments.firstOrNull()?.getArgumentExpression() as? KtCallExpression

        // If we have explicit type arguments present, we cannot provide the quick fix.
        // Similarly, if the `expectedType` forces a specific type (e.g., we are the assignment of a val with explicit type),
        // we cannot change the argument to the flow without producing red code.
        val readOnlyCallableId = if (element.expectedType == null && element.typeArguments.isEmpty()) {
            collectionArgument?.getAssociatedReadOnlyCallableId()
        } else null

        return Context(readOnlyCallableId)
    }
}
