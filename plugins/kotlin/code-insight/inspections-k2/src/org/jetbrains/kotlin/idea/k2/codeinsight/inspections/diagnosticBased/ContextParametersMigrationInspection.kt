// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.rename.NameSuggestionProvider
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinKtDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

internal class InspectionContext(
    val contextReceiversWithNames: Map<SmartPsiElementPointer<KtContextReceiver>, String>,
    val implicitContextUsages: List<ImplicitContextReceiverUsage>,
)

internal class ImplicitContextReceiverUsage(
    val callReference: SmartPsiElementPointer<KtSimpleNameExpression>,
    val receiverCombination: ReceiverCombination,
    val dispatchReceiverContextPsi: SmartPsiElementPointer<KtContextReceiver>?,
    val extensionReceiverContextPsi: SmartPsiElementPointer<KtContextReceiver>?,
)

internal class ContextParametersMigrationInspection :
    KotlinKtDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.ContextParameterWithoutName, InspectionContext>() {

    override val diagnosticType: KClass<KaFirDiagnostic.ContextParameterWithoutName>
        get() = KaFirDiagnostic.ContextParameterWithoutName::class

    override val diagnosticFilter: KaDiagnosticCheckerFilter = KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS

    override fun isApplicableByPsi(element: KtElement): Boolean {
        val languageVersionSettings = element.languageVersionSettings
        return element is KtContextReceiver
                && languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)
                && languageVersionSettings.languageVersion >= LanguageVersion.KOTLIN_2_2
    }

    override fun getApplicableRanges(element: KtElement): List<TextRange> {
        return ApplicabilityRange.single(element) { el ->
            el as? KtContextReceiver
        }
    }

    override fun getProblemDescription(element: KtElement, context: InspectionContext): @InspectionMessage String =
        KotlinBundle.message("inspection.context.parameters.migration.problem.description")

    override fun createQuickFix(element: KtElement, context: InspectionContext): KotlinModCommandQuickFix<KtElement> =
        ContextParametersMigrationQuickFix(context)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitContextReceiverList(contextReceiverList: KtContextReceiverList) {
                visitTargetElement(contextReceiverList, holder, isOnTheFly)
                contextReceiverList.contextReceivers().forEach { contextReceiver ->
                    visitTargetElement(contextReceiver, holder, isOnTheFly)
                }
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.ContextParameterWithoutName
    ): InspectionContext? {
        if (element !is KtContextReceiver) return null
        val containingFunction = element.parentOfTypes(KtNamedFunction::class, KtProperty::class) ?: return null
        val receivers = element.parentOfType<KtContextReceiverList>()?.contextReceivers() ?: return null
        val ktContextReceiverSet = receivers.toSet()

        val implicitContextReceiverUsages = mutableListOf<ImplicitContextReceiverUsage>()
        containingFunction.accept(
            referenceExpressionRecursiveVisitor { referenceExpression ->
                if (referenceExpression !is KtSimpleNameExpression) return@referenceExpressionRecursiveVisitor

                implicitContextReceiverUsages.addIfNotNull(
                    buildImplicitContextUsageInfo(referenceExpression, ktContextReceiverSet)
                )
            }
        )
        val contextNames = findSuggestedContextNames(receivers, containingFunction) ?: createFallbackNames(receivers)

        return InspectionContext(
            contextNames,
            implicitContextReceiverUsages,
        )
    }

    // Create a function copy and add placeholder context parameter names.
    // Then use the name suggestion provider to improve names where possible.
    private fun findSuggestedContextNames(
        contextReceivers: List<KtContextReceiver>,
        containingDeclaration: KtNamedDeclaration,
    ): Map<SmartPsiElementPointer<KtContextReceiver>, String>? {
        val fileCopy = containingDeclaration.containingKtFile.copy() as? KtFile ?: return null
        val declarationCopy = PsiTreeUtil.findSameElementInCopy(containingDeclaration, fileCopy) ?: return null
        val contextReceiverList = when (declarationCopy) {
            is KtNamedFunction -> declarationCopy.contextReceiverList
            is KtProperty -> declarationCopy.contextReceiverList
            else -> null
        }
        val copyContextReceivers = contextReceiverList?.contextReceivers() ?: return null
        val initialNamesForReceivers =
            copyContextReceivers.mapIndexed { index, receiver -> receiver to fallbackContextParamName(index) }.toMap()

        val replaced = initialNamesForReceivers.mapNotNull { (contextReceiver, dummyName) ->
            val parameterText = "$dummyName: ${contextReceiver.typeReference()?.text.orEmpty()}"
            val contextParameter = KtPsiFactory(containingDeclaration.project).createParameter(parameterText)
            contextReceiver.replace(contextParameter) as? KtParameter
        }

        val suggestedNames = replaced.map { contextParameter ->
            val namesHolder = mutableSetOf<String>()
            NameSuggestionProvider.suggestNames(contextParameter, containingDeclaration, namesHolder)
            namesHolder.firstOrNull { it != contextParameter.name } ?: contextParameter.name ?: return null
        }
        if (contextReceivers.size != suggestedNames.size) return null

        return contextReceivers.zip(suggestedNames)
            .associate { (contextReceiver, suggestedName) -> contextReceiver.createSmartPointer() to suggestedName }
    }

    private fun createFallbackNames(receivers: List<KtContextReceiver>): Map<SmartPsiElementPointer<KtContextReceiver>, String> {
        return receivers.mapIndexed { index, receiver -> receiver.createSmartPointer() to fallbackContextParamName(index) }.toMap()
    }

    private fun fallbackContextParamName(index: Int): String = "_context$index"

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.buildImplicitContextUsageInfo(
        referenceExpression: KtSimpleNameExpression,
        ktContextReceiverSet: Set<KtContextReceiver>,
    ): ImplicitContextReceiverUsage? {
        // ignoring constructors; they can have the `context` keyword, but it doesn't seem to be doing anything
        val call = referenceExpression.resolveToCall()?.let { it.singleVariableAccessCall() ?: it.singleFunctionCallOrNull() }
            ?: return null

        // Context-to-context usages are always past implicitly and will work as-is.
        // Check only call's dispatch and extension receivers.
        val dispatchReceiver = call.partiallyAppliedSymbol.dispatchReceiver
        val extensionReceiver = call.partiallyAppliedSymbol.extensionReceiver

        val contextReceiverUsedInDispatch = detectImplicitlyUsedContextReceiver(dispatchReceiver, ktContextReceiverSet)
        val contextReceiverUsedInExtension = detectImplicitlyUsedContextReceiver(extensionReceiver, ktContextReceiverSet)
        if (contextReceiverUsedInDispatch == null && contextReceiverUsedInExtension == null) return null

        val receiverCombination = classifyReceiverCombination(
            dispatchReceiver, contextReceiverUsedInDispatch, extensionReceiver, contextReceiverUsedInExtension
        ) ?: return null

        return ImplicitContextReceiverUsage(
            callReference = referenceExpression.createSmartPointer(),
            receiverCombination = receiverCombination,
            dispatchReceiverContextPsi = contextReceiverUsedInDispatch?.createSmartPointer(),
            extensionReceiverContextPsi = contextReceiverUsedInExtension?.createSmartPointer(),
        )
    }

    private fun classifyReceiverCombination(
        dispatchReceiver: KaReceiverValue?,
        dispatchReceiverContextPsi: KtContextReceiver?,
        extensionReceiver: KaReceiverValue?,
        extensionReceiverContextPsi: KtContextReceiver?,
    ): ReceiverCombination? {
        val hasBothReceivers = dispatchReceiver != null && extensionReceiver != null
        val hasExplicitExtension = extensionReceiver?.unwrapSmartCasts() is KaExplicitReceiverValue

        return when {
            !hasBothReceivers && dispatchReceiverContextPsi == null ->
                ReceiverCombination.CONTEXT_EXTENSION
            !hasBothReceivers && extensionReceiverContextPsi == null ->
                ReceiverCombination.CONTEXT_DISPATCH
            hasBothReceivers && dispatchReceiverContextPsi != null && extensionReceiverContextPsi != null ->
                ReceiverCombination.CONTEXT_EXTENSION_CONTEXT_DISPATCH
            hasBothReceivers && dispatchReceiverContextPsi != null && hasExplicitExtension ->
                ReceiverCombination.EXPLICIT_EXTENSION_CONTEXT_DISPATCH
            hasBothReceivers && dispatchReceiverContextPsi != null && !hasExplicitExtension ->
                ReceiverCombination.IMPLICIT_EXTENSION_CONTEXT_DISPATCH
            hasBothReceivers && dispatchReceiverContextPsi == null && extensionReceiverContextPsi != null ->
                ReceiverCombination.CONTEXT_EXTENSION_IMPLICIT_DISPATCH
            else -> null
        }
    }

    private fun detectImplicitlyUsedContextReceiver(
        receiverValue: KaReceiverValue?,
        allContextReceivers: Set<KtContextReceiver>,
    ): KtContextReceiver? {
        if (receiverValue !is KaImplicitReceiverValue) return null
        return receiverValue.symbol.psi.takeIf { it in allContextReceivers } as? KtContextReceiver
    }
}

/**
 * Combination of call's dispatch and extension receivers for a call that references a context receiver implicitly.
 * The remaining combinations are either incorrect or do not allow context usage.
 */
internal enum class ReceiverCombination {
    /**
     * Only one receiver: dispatch receiver from context
     */
    CONTEXT_DISPATCH,

    /**
     * Only one receiver: extension receiver from context
     */
    CONTEXT_EXTENSION,

    /**
     * Implicit context dispatch receiver in a dot-qualified call
     */
    EXPLICIT_EXTENSION_CONTEXT_DISPATCH,

    /**
     * Two implicit receivers, only the extension receiver comes from a context
     */
    CONTEXT_EXTENSION_IMPLICIT_DISPATCH,

    /**
     * Two implicit receivers, only the dispatch receiver comes from a context
     */
    IMPLICIT_EXTENSION_CONTEXT_DISPATCH,

    /**
     * Two implicit receivers, both receivers come from the same or different contexts
     */
    CONTEXT_EXTENSION_CONTEXT_DISPATCH,
}

internal class ContextParametersMigrationQuickFix(
    internal val context: InspectionContext,
) : KotlinModCommandQuickFix<KtElement>() {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("inspection.context.parameters.migration.quick.fix.text")
    }

    override fun applyFix(
        project: Project,
        element: KtElement,
        updater: ModPsiUpdater,
    ) {
        val factory = KtPsiFactory(project)
        val containingFunctionOrProperty = element.parentOfTypes(KtNamedFunction::class, KtProperty::class) ?: return
        val contextReceiversWithNewNames = context.contextReceiversWithNames.mapNotNull { (contextReceiverPointer, newName) ->
            contextReceiverPointer.element?.to(newName)
        }.toMap()
        // prepare writable copies
        val writableContextReceiversWithNewNames = contextReceiversWithNewNames.mapKeys { (ktReceiver, _) ->
            updater.getWritable(ktReceiver)
        }

        val writableCallElementsForUsages = context.implicitContextUsages.associateWith { usage ->
            val callElement = usage.callReference.element?.let { ref -> ref.parent as? KtCallExpression ?: ref } ?:return
            updater.getWritable(callElement)
        }
        // replace usages from inner to outer
        for (implicitContextUsage in sortedContextUsages(writableCallElementsForUsages)) {
            val dispatchReceiverContextPsi = implicitContextUsage.dispatchReceiverContextPsi
            val extensionReceiverContextPsi = implicitContextUsage.extensionReceiverContextPsi
            val receiverCombination = implicitContextUsage.receiverCombination

            val oldCall = writableCallElementsForUsages[implicitContextUsage] ?: continue
            val newText = newCallExpressionText(
                oldCall, receiverCombination, dispatchReceiverContextPsi?.element,
                extensionReceiverContextPsi?.element, contextReceiversWithNewNames
            ) ?: continue
            oldCall.replace(factory.createExpression(newText))
        }
        // replace receivers with parameters
        for ((writableContextReceiver, newName) in writableContextReceiversWithNewNames) {
            val newContextParameter = writableContextReceiver.createNamedParameter(newName)
            writableContextReceiver.replace(newContextParameter)
        }
        // if there's a single context parameter, suggest renaming in interactive mode
        containingFunctionOrProperty.contextReceiverList?.contextParameters()?.singleOrNull()?.let { contextParameter ->
            val suggestedNames = mutableSetOf<String>()
            NameSuggestionProvider.suggestNames(contextParameter, containingFunctionOrProperty, suggestedNames)
            updater.rename(contextParameter, suggestedNames.toList())
        }
    }

    private fun sortedContextUsages(
        writableCallElementsForUsages: Map<ImplicitContextReceiverUsage, KtReferenceExpression>
    ): List<ImplicitContextReceiverUsage> {
        return context.implicitContextUsages.sortedWith { a, b ->
            val firstCallElement = writableCallElementsForUsages.getValue(a)
            val secondCallElement = writableCallElementsForUsages.getValue(b)
            when {
                PsiTreeUtil.isAncestor(firstCallElement, secondCallElement, true) -> 1
                PsiTreeUtil.isAncestor(secondCallElement, firstCallElement, true) -> -1
                else -> 0
            }
        }
    }

    private fun newCallExpressionText(
        oldCall: KtElement,
        receiverCombination: ReceiverCombination,
        dispatchReceiverContextPsi: KtContextReceiver?,
        extensionReceiverContextPsi: KtContextReceiver?,
        contextRenames: Map<KtContextReceiver, String>,
    ): String? {
        fun KtContextReceiver?.contextParameterName(): String? = this?.let { contextRenames[it] }

        return when (receiverCombination) {
            ReceiverCombination.CONTEXT_DISPATCH -> {
                val contextName = dispatchReceiverContextPsi.contextParameterName() ?: return null
                "$contextName.${oldCall.text}"
            }
            ReceiverCombination.CONTEXT_EXTENSION -> {
                val contextName = extensionReceiverContextPsi.contextParameterName() ?: return null
                "$contextName.${oldCall.text}"
            }
            ReceiverCombination.EXPLICIT_EXTENSION_CONTEXT_DISPATCH -> {
                val contextName = dispatchReceiverContextPsi.contextParameterName() ?: return null
                "let { with($contextName) { it.${oldCall.text} }}"
            }
            ReceiverCombination.CONTEXT_EXTENSION_IMPLICIT_DISPATCH -> {
                val contextName = extensionReceiverContextPsi.contextParameterName() ?: return null
                "$contextName.${oldCall.text}"
            }
            ReceiverCombination.IMPLICIT_EXTENSION_CONTEXT_DISPATCH -> {
                val contextName = dispatchReceiverContextPsi.contextParameterName() ?: return null
                "with($contextName) { ${oldCall.text} }"
            }
            ReceiverCombination.CONTEXT_EXTENSION_CONTEXT_DISPATCH -> {
                val dispatchContextName = dispatchReceiverContextPsi.contextParameterName() ?: return null
                val extensionContextName = extensionReceiverContextPsi.contextParameterName() ?: return null
                "with($dispatchContextName) { $extensionContextName.${oldCall.text} }"
            }
        }
    }
}

private fun KtContextReceiver.createNamedParameter(name: String): KtParameter {
    return KtPsiFactory(project).createParameter("$name: ${typeReference()?.text.orEmpty()}")
}
