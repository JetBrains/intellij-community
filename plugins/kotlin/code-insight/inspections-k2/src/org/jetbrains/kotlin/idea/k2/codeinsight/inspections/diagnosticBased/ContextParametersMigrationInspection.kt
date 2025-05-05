// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.NameSuggestionProvider
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.getLabelToBeReferencedByThis
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.contentRange
import org.jetbrains.kotlin.utils.SmartSet
import java.util.*

internal class InspectionContext(
    val contextReceiversWithNames: Map<SmartPsiElementPointer<KtContextReceiver>, String>,
    val contextReceiversForTopLevelWith: List<SmartPsiElementPointer<KtContextReceiver>>,
    implicitContextUsages: List<ImplicitContextReceiverUsage>,
    labeledThisContextReceiverUsages: List<LabeledThisContextReceiverUsage>,
    shadowedThisUsages: List<ShadowedThisUsage>,
) {
    val allReplaceableElements: List<ReplaceableElement> =
        buildList {
            addAll(implicitContextUsages)
            addAll(labeledThisContextReceiverUsages)
            addAll(shadowedThisUsages)
        }
}

private val LOG = Logger.getInstance(ContextParametersMigrationInspection::class.java)

internal class ContextParametersMigrationInspection : KotlinApplicableInspectionBase.Simple<KtNamedDeclaration, InspectionContext>() {
    override fun isApplicableByPsi(element: KtNamedDeclaration): Boolean {
        val languageVersionSettings = element.languageVersionSettings
        if (element !is KtNamedFunction && element !is KtProperty) return false
        if (!languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) return false
        if (languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_2_2) return false
        return element.contextReceiverList?.contextReceivers()?.isNotEmpty() == true
    }

    override fun getApplicableRanges(element: KtNamedDeclaration): List<TextRange> {
        return ApplicabilityRange.multiple(element) { el ->
            if (el !is KtNamedFunction && el !is KtProperty) return@multiple emptyList()
            el.contextReceiverList?.contextReceivers().orEmpty()
        }
    }

    override fun getProblemDescription(element: KtNamedDeclaration, context: InspectionContext): @InspectionMessage String =
        KotlinBundle.message("inspection.context.parameters.migration.problem.description")

    override fun createQuickFix(element: KtNamedDeclaration, context: InspectionContext): KotlinModCommandQuickFix<KtNamedDeclaration> =
        ContextParametersMigrationQuickFix(context)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                visitTargetElement(function, holder, isOnTheFly)
            }

            override fun visitProperty(property: KtProperty) {
                visitTargetElement(property, holder, isOnTheFly)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtNamedDeclaration): InspectionContext? {
        val containingDeclaration = element
        if (containingDeclaration !is KtNamedFunction && containingDeclaration !is KtProperty) return null
        val receivers = element.contextReceiverList?.contextReceivers() ?: return null
        val ktContextReceiverSet = receivers.toSet()
        val containingDeclarationSymbol = containingDeclaration.symbol

        val implicitContextReceiverUsages = mutableListOf<ImplicitContextReceiverUsage>()
        val labeledThisContextReceiverUsages = mutableListOf<LabeledThisContextReceiverUsage>()
        val shadowedThisUsages = mutableListOf<ShadowedThisUsage>()
        containingDeclaration.accept(
            object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    implicitContextReceiverUsages.addIfNotNull(
                        buildImplicitContextUsageInfo(expression, ktContextReceiverSet)
                    )
                }

                override fun visitThisExpression(expression: KtThisExpression) {
                    labeledThisContextReceiverUsages.addIfNotNull(
                        buildLabeledThisUsageInfo(expression, ktContextReceiverSet)
                    )
                    shadowedThisUsages.addIfNotNull(
                        buildShadowedThisUsageInfo(expression, containingDeclarationSymbol)
                    )
                }
            }
        )

        val implicitOnlyContextReceivers = getImplicitOnlyContextReceivers(implicitContextReceiverUsages)
        val implicitContextUsagesWithLocalReplacement = implicitContextReceiverUsages.filterNot { contextReceiverUsage ->
            implicitOnlyContextReceivers.containsAll(contextReceiverUsage.getAllContextReceivers())
                    && contextReceiverUsage.isInsideBodyOf(containingDeclaration) // not true for default parameters
        }
        val contextNames = findSuggestedContextNames(receivers, containingDeclaration) ?: createFallbackNames(receivers)
        val reviewedShadowedThisUsages = shadowedThisUsages.takeIf { implicitOnlyContextReceivers.isNotEmpty() }.orEmpty()

        return InspectionContext(
            contextNames,
            implicitOnlyContextReceivers.map { it.createSmartPointer() },
            implicitContextUsagesWithLocalReplacement,
            labeledThisContextReceiverUsages,
            reviewedShadowedThisUsages,
        )
    }

    private fun getImplicitOnlyContextReceivers(calls: List<ImplicitContextReceiverUsage>): Set<KtContextReceiver> {
        return calls.filter { it.receiverCombination in receiverCombinationsWithImplicitOnlyContext }
            .mapNotNullTo(SmartSet.create()) { it.getImplicitOnlyContextReceiver() }
    }

    private fun ImplicitContextReceiverUsage.isInsideBodyOf(containingDeclaration: KtNamedDeclaration): Boolean {
        val callReference = callReference.element ?: return false
        return when (containingDeclaration) {
            is KtNamedFunction -> PsiTreeUtil.isAncestor(containingDeclaration.bodyExpression, callReference, true)
            is KtProperty -> PsiTreeUtil.isAncestor(containingDeclaration.getter?.bodyExpression, callReference, true)
                    || PsiTreeUtil.isAncestor(containingDeclaration.setter?.bodyExpression, callReference, true)
            else -> false
        }
    }

    /**
     * Create a function copy and add placeholder context parameter names.
     * Then use the name suggestion provider to improve names where possible.
     */
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

        val nonSuitableNames = mutableSetOf<String>()
        collectAllParameterNames(declarationCopy, nonSuitableNames)
        val suggestedNames = replaced.map { contextParameter ->
            val resultNames = mutableSetOf<String>()
            NameSuggestionProvider.suggestNames(contextParameter, containingDeclaration, resultNames)
            resultNames.firstOrNull { it !in nonSuitableNames }?.also { nonSuitableNames.add(it) }
                ?: contextParameter.name
                ?: return null
        }
        if (contextReceivers.size != suggestedNames.size) return null

        return contextReceivers.zip(suggestedNames)
            .associate { (contextReceiver, suggestedName) -> contextReceiver.createSmartPointer() to suggestedName }
    }

    private fun collectAllParameterNames(declaration: KtNamedDeclaration, result: MutableSet<String>) {
        when (declaration) {
            is KtNamedFunction -> {
                result.addAll(declaration.valueParameters.mapNotNull { it.name })
                result.addAll(declaration.contextReceiverList?.contextParameters()?.mapNotNull { it.name }.orEmpty())
            }
            is KtProperty -> {
                declaration.setter?.parameter?.name?.let { result.add(it) }
                result.addAll(declaration.contextReceiverList?.contextParameters()?.mapNotNull { it.name }.orEmpty())
            }
        }
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
        val unwrapped = receiverValue?.unwrapSmartCasts() ?: return null
        if (unwrapped !is KaImplicitReceiverValue) return null
        return unwrapped.symbol.psi.takeIf { it in allContextReceivers } as? KtContextReceiver
    }

    private fun KaSession.buildLabeledThisUsageInfo(
        thisExpression: KtThisExpression,
        contextReceiverSet: Set<KtContextReceiver>,
    ): LabeledThisContextReceiverUsage? {
        if (thisExpression.labelQualifier == null) return null
        val thisExpressionSymbol = thisExpression.instanceReference.mainReference.resolveToSymbol() ?: return null
        val maybeContextReceiver = thisExpressionSymbol.psi ?: return null

        if (maybeContextReceiver is KtContextReceiver && maybeContextReceiver in contextReceiverSet) {
            return LabeledThisContextReceiverUsage(
                thisExpression.createSmartPointer(),
                maybeContextReceiver.createSmartPointer()
            )
        }
        return null
    }

    private fun KaSession.buildShadowedThisUsageInfo(
        thisExpression: KtThisExpression,
        contextOwnerSymbol: KaDeclarationSymbol,
    ): ShadowedThisUsage? {
        // detection of outer `this@with`shadowing is not supported, can only happen with local classes
        if (thisExpression.labelQualifier != null) return null
        val symbolReferencedByThis = thisExpression.instanceReference.mainReference.resolveToSymbol() ?: return null
        val implicitReceiversAtThisRef = thisExpression.containingKtFile.scopeContext(thisExpression).implicitReceivers
        // either the context owner or the containing class-like (if any) can be a provider of the default implicit `this`
        // proper context owner cannot be a local callable, so we are not checking the whole chain of containing declarations
        val interestingThisProviders = listOfNotNull(contextOwnerSymbol, contextOwnerSymbol.containingDeclaration)
        val shadowedThisOwner = implicitReceiversAtThisRef.firstOrNull { implicitReceiver ->
            implicitReceiver.ownerSymbol in interestingThisProviders
        }?.ownerSymbol ?: return null
        if (symbolReferencedByThis.owningCallableSymbolIfReceiverOrSelf() != shadowedThisOwner) return null
        val label = getLabelToBeReferencedByThis(shadowedThisOwner)?.receiverLabel ?: return null
        return ShadowedThisUsage(thisExpression.createSmartPointer(), label)
    }

    private fun KaSymbol.owningCallableSymbolIfReceiverOrSelf(): KaSymbol =
        if (this is KaReceiverParameterSymbol) this.owningCallableSymbol else this
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

private val receiverCombinationsWithImplicitOnlyContext: EnumSet<ReceiverCombination> =
    EnumSet.of(
        ReceiverCombination.EXPLICIT_EXTENSION_CONTEXT_DISPATCH,
        ReceiverCombination.IMPLICIT_EXTENSION_CONTEXT_DISPATCH,
        ReceiverCombination.CONTEXT_EXTENSION_CONTEXT_DISPATCH,
    )

internal class ContextParametersMigrationQuickFix(
    internal val context: InspectionContext,
) : KotlinModCommandQuickFix<KtNamedDeclaration>() {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("inspection.context.parameters.migration.quick.fix.text")
    }

    override fun applyFix(
        project: Project,
        element: KtNamedDeclaration,
        updater: ModPsiUpdater,
    ) {
        val factory = KtPsiFactory(project)
        if (element !is KtNamedFunction && element !is KtProperty) return
        val containingFunctionOrProperty = element
        val contextReceiversWithNewNames = context.contextReceiversWithNames.mapNotNull { (contextReceiverPointer, newName) ->
            contextReceiverPointer.element?.to(newName)
        }.toMap()
        // prepare writable copies
        val writableContextReceiversWithNewNames = contextReceiversWithNewNames.mapKeys { (ktReceiver, _) ->
            updater.getWritable(ktReceiver)
        }
        val writableElementsForContextUsages = prepareWritableElementsForContextUsages(updater) ?: return
        // replace affected elements from inner to outer for correct composite results
        val sortedElementsToReplace = sortedElementsFromInnerToOuter(
            context.allReplaceableElements,
            writableElementsForContextUsages,
        )
        for (elementToReplace in sortedElementsToReplace) {
            val oldElement = writableElementsForContextUsages[elementToReplace] ?: continue
            val newElement = elementToReplace.provideReplacement(oldElement, factory, contextReceiversWithNewNames)
                ?: continue
            oldElement.replace(newElement)
        }
        // wrap body content in the remaining `with(someContext)...` calls after all replacements inside are done
        val contextNames = context.contextReceiversForTopLevelWith.mapNotNull { contextReceiversWithNewNames[it.element] }
        when (containingFunctionOrProperty) {
            is KtNamedFunction -> wrapBodyWithContexts(containingFunctionOrProperty, contextNames, factory)
            is KtProperty -> {
                containingFunctionOrProperty.getter?.let { wrapBodyWithContexts(it, contextNames, factory) }
                containingFunctionOrProperty.setter?.let { wrapBodyWithContexts(it, contextNames, factory) }
            }
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

    private fun prepareWritableElementsForContextUsages(updater: ModPsiUpdater): Map<ReplaceableElement, KtElement>? =
        context.allReplaceableElements.associateWith { replaceableElement ->
            val elementToReplace = replaceableElement.provideElementForReplacement() ?: return null
            updater.getWritable(elementToReplace)
        }

    private fun sortedElementsFromInnerToOuter(
        replaceableElementsToSort: List<ReplaceableElement>,
        referenceElementsMap: Map<in ReplaceableElement, KtElement>
    ): List<ReplaceableElement> {
        return replaceableElementsToSort.sortedWith { a, b ->
            val firstCallElement = referenceElementsMap.getValue(a)
            val secondCallElement = referenceElementsMap.getValue(b)
            when {
                PsiTreeUtil.isAncestor(firstCallElement, secondCallElement, true) -> 1
                PsiTreeUtil.isAncestor(secondCallElement, firstCallElement, true) -> -1
                else -> 0
            }
        }
    }

    private fun wrapBodyWithContexts(declaration: KtDeclarationWithBody, contextNames: List<String>, ktPsiFactory: KtPsiFactory) {
        if (contextNames.isEmpty()) return
        val body = declaration.bodyExpression ?: return
        val bodyContentRange = if (body is KtBlockExpression) body.contentRange() else PsiChildRange.singleElement(body)
        val contentWrappedWithContexts = wrapWithContexts(bodyContentRange, contextNames, ktPsiFactory) ?: return
        val newBody = if (body is KtBlockExpression) {
            ktPsiFactory.createEmptyBody().apply {
                addAfter(contentWrappedWithContexts, lBrace!!)
            }
        } else {
            contentWrappedWithContexts
        }
        body.replace(newBody)
    }

    private fun wrapWithContexts(childRange: PsiChildRange, contextNames: List<String>, ktPsiFactory: KtPsiFactory): KtExpression? {
        if (contextNames.isEmpty()) return null
        // to keep the names in the order of declaration, wrapping happens from inner to outer `with`
        val reversedNames = contextNames.asReversed()
        val firstName = reversedNames.first()
        val initialWrapping = wrapWithContext(childRange, firstName, ktPsiFactory) ?: return null
        return reversedNames.drop(1).fold(initialWrapping) { forNextWrapping, nextContextName ->
            wrapWithContext(forNextWrapping, nextContextName, ktPsiFactory) ?: forNextWrapping
        }
    }

    private fun wrapWithContext(range: PsiChildRange, contextName: String, ktPsiFactory: KtPsiFactory): KtExpression? {
        val first = range.firstOrNull() ?: return null
        val (withContextExpression, lambdaBody) = createWithContextCallAndFindLambdaBody(contextName, ktPsiFactory) ?: return null
        lambdaBody.addRangeAfter(first, range.last(), lambdaBody.lBrace)
        return withContextExpression
    }

    private fun wrapWithContext(element: KtElement, contextName: String, ktPsiFactory: KtPsiFactory): KtExpression? {
        val (withContextExpression, lambdaBody) = createWithContextCallAndFindLambdaBody(contextName, ktPsiFactory) ?: return null
        lambdaBody.addAfter(element, lambdaBody.lBrace)
        return withContextExpression
    }

    private fun createWithContextCallAndFindLambdaBody(
        contextName: String, ktPsiFactory: KtPsiFactory
    ): Pair<KtExpression, KtBlockExpression>? {
        val withContextExpression = ktPsiFactory.createExpression("with($contextName) {}")
        val lastLambdaArgument = withContextExpression.lastChild as? KtLambdaArgument
        val lambdaBody =  lastLambdaArgument?.getLambdaExpression()?.bodyExpression ?: run {
            LOG.error("Unexpected failure to get the lambda body from the expression: ${withContextExpression.text}")
            return null
        }
        return withContextExpression to lambdaBody
    }
}

internal interface ReplaceableElement {
    fun provideReplacement(
        oldElement: KtElement,
        ktPsiFactory: KtPsiFactory,
        contextReceiversWithNewNames: Map<KtContextReceiver, String>,
    ): KtElement?

    fun provideElementForReplacement(): KtElement?
}

internal class ImplicitContextReceiverUsage(
    val callReference: SmartPsiElementPointer<KtSimpleNameExpression>,
    val receiverCombination: ReceiverCombination,
    val dispatchReceiverContextPsi: SmartPsiElementPointer<KtContextReceiver>?,
    val extensionReceiverContextPsi: SmartPsiElementPointer<KtContextReceiver>?,
): ReplaceableElement {
    override fun toString(): String =
        """
            call: ${callReference.element?.text}
            receiver combination: $receiverCombination
            dispatch receiver: ${dispatchReceiverContextPsi?.element?.text}
            extension receiver: ${extensionReceiverContextPsi?.element?.text}
        """.trimIndent()

    override fun provideReplacement(
        oldElement: KtElement,
        ktPsiFactory: KtPsiFactory,
        contextReceiversWithNewNames: Map<KtContextReceiver, String>,
    ): KtElement? {
        val newText = newCallExpressionText(
            oldElement, receiverCombination, dispatchReceiverContextPsi?.element,
            extensionReceiverContextPsi?.element, contextReceiversWithNewNames
        ) ?: return null
        return ktPsiFactory.createExpression(newText)
    }

    override fun provideElementForReplacement(): KtElement? =
        callReference.element?.let { ref -> ref.parent as? KtCallExpression ?: ref }

    /**
     * Context dispatch receiver in all types of calls with two receivers can't be made explicit
     */
    fun getImplicitOnlyContextReceiver(): KtContextReceiver? {
        return when (receiverCombination) {
            ReceiverCombination.CONTEXT_DISPATCH,
            ReceiverCombination.CONTEXT_EXTENSION,
            ReceiverCombination.CONTEXT_EXTENSION_IMPLICIT_DISPATCH -> null
            ReceiverCombination.EXPLICIT_EXTENSION_CONTEXT_DISPATCH,
            ReceiverCombination.IMPLICIT_EXTENSION_CONTEXT_DISPATCH,
            ReceiverCombination.CONTEXT_EXTENSION_CONTEXT_DISPATCH -> dispatchReceiverContextPsi?.element
        }
    }

    fun getAllContextReceivers(): Set<KtContextReceiver> =
        setOfNotNull(dispatchReceiverContextPsi?.element, extensionReceiverContextPsi?.element)

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
            ReceiverCombination.CONTEXT_EXTENSION,
            ReceiverCombination.CONTEXT_EXTENSION_IMPLICIT_DISPATCH,
            ReceiverCombination.CONTEXT_EXTENSION_CONTEXT_DISPATCH -> {
                val contextName = extensionReceiverContextPsi.contextParameterName() ?: return null
                "$contextName.${oldCall.text}"
            }
            ReceiverCombination.EXPLICIT_EXTENSION_CONTEXT_DISPATCH,
            ReceiverCombination.IMPLICIT_EXTENSION_CONTEXT_DISPATCH -> null
        }
    }
}

internal class LabeledThisContextReceiverUsage(
    val callReference: SmartPsiElementPointer<KtThisExpression>,
    val receiverContextPsi: SmartPsiElementPointer<KtContextReceiver>,
): ReplaceableElement {
    override fun toString(): String =
        """
            this expression: ${callReference.element?.text} 
            context receiver: ${receiverContextPsi.element?.text}
            """.trimIndent()

    override fun provideReplacement(
        oldElement: KtElement,
        ktPsiFactory: KtPsiFactory,
        contextReceiversWithNewNames: Map<KtContextReceiver, String>,
    ): KtElement? {
        return contextReceiversWithNewNames[receiverContextPsi.element]?.let { newName ->
            ktPsiFactory.createExpression(newName)
        }
    }

    override fun provideElementForReplacement(): KtElement? = callReference.element
}

internal class ShadowedThisUsage(
    val thisReference: SmartPsiElementPointer<KtThisExpression>,
    val qualifierToAdd: Name,
): ReplaceableElement {
    override fun provideReplacement(
        oldElement: KtElement,
        ktPsiFactory: KtPsiFactory,
        contextReceiversWithNewNames: Map<KtContextReceiver, String>
    ): KtElement = ktPsiFactory.createThisExpression(qualifierToAdd.asString())

    override fun provideElementForReplacement(): KtElement? = thisReference.element
}

private fun KtContextReceiver.createNamedParameter(name: String): KtParameter {
    return KtPsiFactory(project).createParameter("$name: ${typeReference()?.text.orEmpty()}")
}
