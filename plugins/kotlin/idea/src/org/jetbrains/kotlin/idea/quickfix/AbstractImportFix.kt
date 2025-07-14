// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.ImportFilter
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.*
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.elementType
import com.intellij.util.Processors
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.actions.*
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.isImported
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.UnresolvedReferenceQuickFixFactory
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.core.util.getResolveScope
import org.jetbrains.kotlin.idea.imports.canBeReferencedViaImport
import org.jetbrains.kotlin.idea.imports.getConstructors
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.getCallableDescriptor
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiUtil.isSelectorInQualified
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.inference.model.TypeVariableTypeConstructor
import org.jetbrains.kotlin.resolve.calls.util.getParentCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtensionProperty
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.ExplicitImportsScope
import org.jetbrains.kotlin.resolve.scopes.utils.addImportingScope
import org.jetbrains.kotlin.resolve.scopes.utils.collectFunctions
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Check possibility and perform fix for unresolved references.
 */
@IntellijInternalApi
abstract class ImportFixBase<T : KtExpression> protected constructor(
    expression: T,
    private val expressionToAnalyzePointer: SmartPsiElementPointer<KtExpression>?,
    factory: Factory
) : KotlinImportQuickFixAction<T>(expression), HintAction, HighPriorityAction {

    constructor(expression: T, factory: Factory):
        this(expression, null, factory)

    constructor(expression: T, expressionToAnalyze: KtExpression, factory: Factory):
        this(expression, expressionToAnalyze.createSmartPointer(), factory)

    private val project = expression.project

    private val modificationCountOnCreate = PsiModificationTracker.getInstance(project).modificationCount

    protected val expressionToAnalyze: KtExpression?
        get() = expressionToAnalyzePointer?.element ?: element

    protected lateinit var suggestions: Collection<FqName>

    @IntentionName
    private lateinit var text: String

    internal fun computeSuggestions() {
        val suggestionDescriptors = collectSuggestionDescriptors()
        suggestions = collectSuggestions(suggestionDescriptors)
        text = calculateText(suggestionDescriptors)
    }

    protected open val supportedErrors = factory.supportedErrors.toSet()

    protected abstract val importNames: Collection<Name>
    protected abstract fun getCallTypeAndReceiver(): CallTypeAndReceiver<*, *>?

    protected open fun calculateReceiverTypeFromDiagnostic(diagnostic: Collection<Diagnostic>): KotlinType? = null

    protected fun getReceiverTypeFromDiagnostic(): KotlinType? {
        // it is forbidden to keep `diagnostic` as class property as it leads to leakage of IdeaResolverForProject
        // when quick fix is about to apply we have to (re)calculate diagnostics
        val expression = expressionToAnalyze
        val bindingContext = expression?.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS) ?: return null
        val diagnostics = bindingContext.diagnostics.forElement(expression)
        return calculateReceiverTypeFromDiagnostic(diagnostics)
    }

    override fun showHint(editor: Editor): Boolean {
        val element = element?.takeIf(PsiElement::isValid) ?: return false

        if (isOutdated()) return false

        if (ApplicationManager.getApplication().isHeadlessEnvironment ||
            !DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled ||
            HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        if (suggestions.isEmpty()) return false

        return createAction(editor, element, suggestions).showHint()
    }

    @IntentionName
    private fun calculateText(suggestionDescriptors: Collection<DeclarationDescriptor>): String {
        val descriptors  =
            suggestionDescriptors.mapTo(hashSetOf()) { it.original }.takeIf { it.isNotEmpty() } ?: return ""

        val ktFile = element?.containingKtFile ?: return KotlinBundle.message("fix.import")
        val prioritizer = createPrioritizerForFile(ktFile)
        val expressionWeigher = ExpressionWeigher.createWeigher(element)

        val importInfos = descriptors.mapNotNull { descriptor ->
            val kind = when {
                descriptor.isExtensionProperty -> ImportFixHelper.ImportKind.EXTENSION_PROPERTY
                descriptor is PropertyDescriptor -> ImportFixHelper.ImportKind.PROPERTY
                descriptor is ClassConstructorDescriptor -> ImportFixHelper.ImportKind.CLASS
                descriptor is TypeAliasConstructorDescriptor -> ImportFixHelper.ImportKind.TYPE_ALIAS
                descriptor is FunctionDescriptor && descriptor.isOperator -> ImportFixHelper.ImportKind.OPERATOR
                descriptor is FunctionDescriptor && descriptor.isExtension -> ImportFixHelper.ImportKind.EXTENSION_FUNCTION
                descriptor is FunctionDescriptor -> ImportFixHelper.ImportKind.FUNCTION
                DescriptorUtils.isObject(descriptor) -> ImportFixHelper.ImportKind.OBJECT
                descriptor is ClassDescriptor && descriptor.kind == ClassKind.ENUM_ENTRY -> ImportFixHelper.ImportKind.ENUM_ENTRY
                descriptor is ClassDescriptor -> ImportFixHelper.ImportKind.CLASS
                descriptor is TypeAliasDescriptor -> ImportFixHelper.ImportKind.TYPE_ALIAS
                else -> null
            } ?: return@mapNotNull null

            val name = buildString {
                if (
                    descriptor is CallableDescriptor ||
                    descriptor is ClassDescriptor && descriptor.kind == ClassKind.ENUM_ENTRY
                ) {
                    val extensionReceiverParameter = (descriptor as? CallableDescriptor)?.extensionReceiverParameter
                    if (extensionReceiverParameter != null) {
                        extensionReceiverParameter.type.constructor.declarationDescriptor.safeAs<ClassDescriptor>()?.name?.let {
                            append(it.asString())
                        }
                    } else {
                        descriptor.containingDeclaration.safeAs<ClassifierDescriptor>()?.name?.let {
                            append(it.asString())
                        }
                    }
                }

                descriptor.name.takeUnless { it.isSpecial }?.let {
                    if (this.isNotEmpty()) append('.')
                    append(it.asString())
                }
            }
            val priority = createDescriptorPriority(prioritizer, expressionWeigher, descriptor)

            ImportFixHelper.ImportInfo(kind, name, priority)
        }
        return ImportFixHelper.calculateTextForFix(importInfos, suggestions)
    }

    override fun getText(): String = text

    override fun getFamilyName() = KotlinBundle.message("fix.import")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return element != null && suggestions.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        CommandProcessor.getInstance().runUndoTransparentAction {
            createAction(editor!!, element, suggestions).execute()
        }
    }

    override fun startInWriteAction() = false

    private fun isOutdated() = modificationCountOnCreate != PsiModificationTracker.getInstance(project).modificationCount

    protected open fun createAction(editor: Editor, element: KtExpression, suggestions: Collection<FqName>): KotlinAddImportAction {
        return createSingleImportAction(element.project, editor, element, suggestions)
    }

    override fun createImportAction(editor: Editor, file: KtFile): QuestionAction? =
        element?.let { createAction(editor, it, suggestions) }

    override fun createAutoImportAction(
        editor: Editor,
        file: KtFile,
        filterSuggestions: (Collection<FqName>) -> Collection<FqName>,
    ): QuestionAction? {
        val suggestions = filterSuggestions(suggestions)
        if (suggestions.isEmpty() || !ImportFixHelper.suggestionsAreFromSameParent(suggestions)) return null

        // we do not auto-import nested classes because this will probably add qualification into the text and this will confuse the user
        if (suggestions.any { suggestion -> file.resolveImportReference(suggestion).any(::isNestedClassifier) }) return null

        return element?.let { createAction(editor, it, suggestions) }
    }

    private fun isNestedClassifier(declaration: DeclarationDescriptor): Boolean =
        declaration is ClassifierDescriptor && declaration.containingDeclaration is ClassifierDescriptor

    private fun collectSuggestionDescriptors(): Collection<DeclarationDescriptor> {
        element?.takeIf(PsiElement::isValid)?.takeIf { it.containingFile is KtFile } ?: return emptyList()

        val callTypeAndReceiver = getCallTypeAndReceiver() ?: return emptyList()
        if (callTypeAndReceiver is CallTypeAndReceiver.UNKNOWN) return emptyList()

        return importNames.flatMap { collectSuggestionsForName(it, callTypeAndReceiver) }
    }

    private fun collectSuggestions(suggestionDescriptors: Collection<DeclarationDescriptor>): Collection<FqName> =
        suggestionDescriptors
            .asSequence()
            .map { it.fqNameSafe }
            .distinct()
            .toList()

    private fun collectSuggestionsForName(name: Name, callTypeAndReceiver: CallTypeAndReceiver<*, *>): Collection<DeclarationDescriptor> {
        val element = element ?: return emptyList()
        val expressionToAnalyze = expressionToAnalyze ?: return emptyList()
        val nameStr = name.asString()
        if (nameStr.isEmpty()) return emptyList()

        val file = element.containingKtFile

        val bindingContext = expressionToAnalyze.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        if (!checkErrorStillPresent(bindingContext)) return emptyList()

        val searchScope = getResolveScope(file)

        val resolutionFacade = file.getResolutionFacade()

        fun isVisible(descriptor: DeclarationDescriptor): Boolean = descriptor.safeAs<DeclarationDescriptorWithVisibility>()
            ?.isVisible(element, callTypeAndReceiver.receiver as? KtExpression, bindingContext, resolutionFacade) ?: true

        val indicesHelper = KotlinIndicesHelper(resolutionFacade, searchScope, ::isVisible, file = file)

        var result = fillCandidates(nameStr, callTypeAndReceiver, bindingContext, indicesHelper)

        // for CallType.DEFAULT do not include functions if there is no parenthesis
        if (callTypeAndReceiver is CallTypeAndReceiver.DEFAULT) {
            val isCall = element.parent is KtCallExpression
            if (!isCall) {
                result = result.filter { it !is FunctionDescriptor }
            }
        }

        result = result.filter { ImportFilter.shouldImport(file, it.fqNameSafe.asString()) }

        return if (result.size > 1)
            reduceCandidatesBasedOnDependencyRuleViolation(result, file)
        else
            result
    }

    private fun checkErrorStillPresent(bindingContext: BindingContext): Boolean {
        val errors = supportedErrors
        val elementsToCheckDiagnostics = elementsToCheckDiagnostics()
        for (psiElement in elementsToCheckDiagnostics) {
            if (bindingContext.diagnostics.forElement(psiElement).any { it.factory in errors }) return true
        }
        return false
    }

    protected open fun elementsToCheckDiagnostics(): Collection<PsiElement> = listOfNotNull(element)

    abstract fun fillCandidates(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext,
        indicesHelper: KotlinIndicesHelper
    ): List<DeclarationDescriptor>

    private fun reduceCandidatesBasedOnDependencyRuleViolation(
        candidates: Collection<DeclarationDescriptor>, file: PsiFile
    ): Collection<DeclarationDescriptor> {
        val project = file.project
        val validationManager = DependencyValidationManager.getInstance(project)
        return candidates.filter {
            val targetFile = DescriptorToSourceUtilsIde.getAnyDeclaration(project, it)?.containingFile ?: return@filter true
            validationManager.getViolatorDependencyRules(file, targetFile).isEmpty()
        }
    }

    abstract class Factory : KotlinSingleIntentionActionFactory() {
        val supportedErrors: Collection<DiagnosticFactory<*>> by lazy { QuickFixes.getInstance().getDiagnostics(this) }

        override fun isApplicableForCodeFragment() = true

        abstract fun createImportAction(diagnostic: Diagnostic): ImportFixBase<*>?

        override fun areActionsAvailable(diagnostic: Diagnostic): Boolean {
            val element = diagnostic.psiElement
            return element is KtExpression && element.references.isNotEmpty()
        }

        open fun createImportActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<ImportFixBase<*>> = emptyList()

        final override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            return try {
                createImportAction(diagnostic)?.also { it.computeSuggestions() }
            } catch (ex: KotlinExceptionWithAttachments) {
                // Sometimes fails with
                // <production sources for module light_idea_test_case> is a module[ModuleDescriptorImpl@508c55a2] is not contained in resolver...
                // TODO: remove try-catch when the problem is fixed
                if (AbstractImportFixInfo.IGNORE_MODULE_ERROR &&
                    ex.message?.contains("<production sources for module light_idea_test_case>") == true
                ) null
                else throw ex
            }
        }

        override fun doCreateActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<IntentionAction> =
            createImportActionsForAllProblems(sameTypeDiagnostics).onEach { it.computeSuggestions() }
    }

    abstract class FactoryWithUnresolvedReferenceQuickFix: Factory(), UnresolvedReferenceQuickFixFactory
}

@IntellijInternalApi
abstract class OrdinaryImportFixBase<T : KtExpression>(expression: T, factory: Factory) : ImportFixBase<T>(expression, factory) {
    override fun fillCandidates(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext,
        indicesHelper: KotlinIndicesHelper
    ): List<DeclarationDescriptor> {
        val expression = element ?: return emptyList()

        val result = ArrayList<DeclarationDescriptor>()

        if (expression is KtSimpleNameExpression) {
            if (!expression.isImportDirectiveExpression() && !isSelectorInQualified(expression)) {
                ProgressManager.checkCanceled()
                val filterByCallType = callTypeAndReceiver.toFilter()

                indicesHelper.getClassifiersByName(expression, name).filterTo(result, filterByCallType)

                indicesHelper.getTopLevelCallablesByName(name).filterTo(result, filterByCallType)
            }
            if (callTypeAndReceiver.callType == CallType.OPERATOR) {
                val type = expression.getCallableDescriptor()?.returnType ?: getReceiverTypeFromDiagnostic()
                if (type != null) {
                    result.addAll(indicesHelper.getCallableTopLevelExtensions(callTypeAndReceiver, listOf(type), { it == name }))
                }
            }
        }
        ProgressManager.checkCanceled()

        result.addAll(
            indicesHelper.getCallableTopLevelExtensions(
                callTypeAndReceiver,
                expression,
                bindingContext,
                findReceiverForDelegate(expression, callTypeAndReceiver.callType)
            ) { it == name }
        )

        val ktFile = element?.containingKtFile ?: return emptyList()
        val importedFqNamesAsAlias = getImportedFqNamesAsAlias(ktFile)
        val (defaultImports, excludedImports) = ImportInsertHelperImpl.computeDefaultAndExcludedImports(ktFile)
        return result.filter {
            val descriptor = it.takeUnless { expression.parent is KtCallExpression && it.isSealed() } ?: return@filter false
            val importableFqName = descriptor.importableFqName ?: return@filter true
            val importPath = ImportPath(importableFqName, isAllUnder = false)
            !importPath.isImported(defaultImports, excludedImports) || importableFqName in importedFqNamesAsAlias
        }
    }

    private fun findReceiverForDelegate(expression: KtExpression, callType: CallType<*>): KotlinType? {
        if (callType != CallType.DELEGATE) return null

        val receiverTypeFromDiagnostic = getReceiverTypeFromDiagnostic()
        if (receiverTypeFromDiagnostic?.constructor is TypeVariableTypeConstructor) {
            if (receiverTypeFromDiagnostic == expression.getCallableDescriptor()?.returnType) {
                // the issue is that the whole lambda expression cannot be resolved,
                // but it's possible to analyze the last expression independently and try guessing the receiver
                return tryFindReceiverFromLambda(expression)
            }
        }

        return receiverTypeFromDiagnostic
    }

    private fun tryFindReceiverFromLambda(expression: KtExpression): KotlinType? {
        if (expression !is KtCallExpression) return null
        val lambdaExpression = expression.singleLambdaArgumentExpression() ?: return null

        val lastStatement = KtPsiUtil.getLastStatementInABlock(lambdaExpression.bodyExpression) ?: return null
        val bindingContext = lastStatement.analyze(bodyResolveMode = BodyResolveMode.PARTIAL)
        return bindingContext.getType(lastStatement)
    }

    private fun getImportedFqNamesAsAlias(ktFile: KtFile) =
        ktFile.importDirectives
            .filter { it.alias != null }
            .mapNotNull { it.importedFqName }
}

// This is required to be abstract to reduce bunch file size
@IntellijInternalApi
abstract class AbstractImportFix(expression: KtSimpleNameExpression, factory: Factory) :
    OrdinaryImportFixBase<KtSimpleNameExpression>(expression, factory) {

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.detect(it) }

    private fun importNamesForMembers(): Collection<Name> {
        val element = element ?: return emptyList()

        if (element.getIdentifier() != null) {
            val name = element.getReferencedName()
            if (Name.isValidIdentifier(name)) {
                return listOf(Name.identifier(name))
            }
        }

        return emptyList()
    }

    override val importNames: Collection<Name> =
        ((element?.mainReference?.resolvesByNames ?: emptyList()) + importNamesForMembers()).distinct()

    private fun collectMemberCandidates(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext,
        indicesHelper: KotlinIndicesHelper
    ): List<DeclarationDescriptor> {
        val element = element ?: return emptyList()
        if (element.isImportDirectiveExpression()) return emptyList()

        val result = ArrayList<DeclarationDescriptor>()

        val filterByCallType = callTypeAndReceiver.toFilter()

        indicesHelper.getKotlinEnumsByName(name).filterTo(result, filterByCallType)

        ProgressManager.checkCanceled()

        val actualReceivers = getReceiversForExpression(element, callTypeAndReceiver, bindingContext)

        if (isSelectorInQualified(element) && actualReceivers.explicitReceivers.isEmpty()) {
            // If the element is qualified, and at the same time we haven't found any explicit
            // receiver, it means that the qualifier is not a value (for example, it might be a type name).
            // In this case we do not want to propose any importing fix, since it is impossible
            // to import a function which can be syntactically called on a non-value qualifier -
            // such function (for example, a static function) should be successfully resolved
            // without any import
            return emptyList()
        }

        val checkDispatchReceiver = when (callTypeAndReceiver) {
            is CallTypeAndReceiver.OPERATOR, is CallTypeAndReceiver.INFIX -> true
            else -> false
        }

        val processor = { descriptor: CallableDescriptor ->
            ProgressManager.checkCanceled()
            if (descriptor.canBeReferencedViaImport() && filterByCallType(descriptor)) {
                if (descriptor.extensionReceiverParameter != null) {
                    result.addAll(
                        descriptor.substituteExtensionIfCallable(
                            actualReceivers.explicitReceivers.ifEmpty { actualReceivers.allReceivers },
                            callTypeAndReceiver.callType
                        )
                    )
                } else if (descriptor.isValidByReceiversFor(actualReceivers, checkDispatchReceiver)) {
                    result.add(descriptor)
                }
            }
        }

        indicesHelper.processKotlinCallablesByName(
            name,
            filter = { declaration -> (declaration.parent as? KtClassBody)?.parent is KtObjectDeclaration },
            processor = processor
        )

        indicesHelper.processAllCallablesInSubclassObjects(
            name,
            callTypeAndReceiver, element, bindingContext,
            processor = processor
        )

        if (element.containingKtFile.platform.isJvm()) {
            indicesHelper.processJvmCallablesByName(
                name,
                filter = { it.hasModifierProperty(PsiModifier.STATIC) },
                processor = processor
            )
        }
        return result
    }

    /**
     * Currently at most one explicit receiver can be used in expression, but it can change in the future,
     * so we use `Collection` to represent explicit receivers.
     */
    private class Receivers(val explicitReceivers: Collection<KotlinType>, val allReceivers: Collection<KotlinType>)

    private fun getReceiversForExpression(
        element: KtSimpleNameExpression,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext
    ): Receivers {
        val resolutionFacade = element.getResolutionFacade()
        val actualReceiverTypes = callTypeAndReceiver
            .receiverTypesWithIndex(
                bindingContext, element,
                resolutionFacade.moduleDescriptor, resolutionFacade,
                stableSmartCastsOnly = false,
                withImplicitReceiversWhenExplicitPresent = true
            ).orEmpty()

        val explicitReceiverType = actualReceiverTypes.filterNot { it.implicit }

        return Receivers(
            explicitReceiverType.map { it.type },
            actualReceiverTypes.map { it.type }
        )
    }

    /**
     * This method accepts only callables with no extension receiver because it ignores generics
     * and does not perform any substitution.
     *
     * @return true iff [this] descriptor can be called given [actualReceivers] present in scope AND
     * passed [Receivers.explicitReceivers] are satisfied if present.
     */
    private fun CallableDescriptor.isValidByReceiversFor(actualReceivers: Receivers, checkDispatchReceiver: Boolean): Boolean {
        require(extensionReceiverParameter == null) { "This method works only on non-extension callables, got $this" }

        val dispatcherReceiver = dispatchReceiverParameter.takeIf { checkDispatchReceiver }

        return if (dispatcherReceiver == null) {
            actualReceivers.explicitReceivers.isEmpty()
        } else {
            val typesToCheck = with(actualReceivers) { explicitReceivers.ifEmpty { allReceivers } }
            typesToCheck.any { it.isSubtypeOf(dispatcherReceiver.type) }
        }
    }

    override fun fillCandidates(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext,
        indicesHelper: KotlinIndicesHelper
    ): List<DeclarationDescriptor> =
        super.fillCandidates(name, callTypeAndReceiver, bindingContext, indicesHelper) + collectMemberCandidates(
            name,
            callTypeAndReceiver,
            bindingContext,
            indicesHelper
        )
}

internal class ImportConstructorReferenceFix(expression: KtSimpleNameExpression) :
    ImportFixBase<KtSimpleNameExpression>(expression, MyFactory) {
    override fun getCallTypeAndReceiver() = element?.let {
        CallTypeAndReceiver.detect(it) as? CallTypeAndReceiver.CALLABLE_REFERENCE
    }

    override fun fillCandidates(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext,
        indicesHelper: KotlinIndicesHelper
    ): List<DeclarationDescriptor> {
        val expression = element ?: return emptyList()

        val filterByCallType = callTypeAndReceiver.toFilter()
        return indicesHelper.getClassifiersByName(expression, name)
            .asSequence()
            .flatMap { it.getConstructors() }
            .filter { it.importableFqName != null }
            .filter(filterByCallType)
            .toList()
    }

    override fun createAction(editor: Editor, element: KtExpression, suggestions: Collection<FqName>): KotlinAddImportAction {
        return createSingleImportActionForConstructor(element.project, editor, element, suggestions)
    }

    override val importNames = element?.mainReference?.resolvesByNames ?: emptyList()

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        override fun createImportAction(diagnostic: Diagnostic) =
            diagnostic.psiElement.safeAs<KtSimpleNameExpression>()?.let(::ImportConstructorReferenceFix)
    }
}

internal class InvokeImportFix(
    expression: KtExpression
) : OrdinaryImportFixBase<KtExpression>(expression, MyFactory) {

    override val importNames = listOf(OperatorNameConventions.INVOKE)

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.OPERATOR(it) }

    override fun calculateReceiverTypeFromDiagnostic(diagnostics: Collection<Diagnostic>): KotlinType? =
        diagnostics.firstOrNull { it.factory == Errors.FUNCTION_EXPECTED }
            ?.let { Errors.FUNCTION_EXPECTED.cast(it).b }

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        override fun createImportAction(diagnostic: Diagnostic) =
            diagnostic.psiElement.safeAs<KtExpression>()?.let {
                InvokeImportFix(it)
            }
    }
}

internal class IteratorImportFix(expression: KtExpression) : OrdinaryImportFixBase<KtExpression>(expression, MyFactory) {
    override val importNames = listOf(OperatorNameConventions.ITERATOR)

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.OPERATOR(it) }

    companion object MyFactory : Factory() {
        override fun createImportAction(diagnostic: Diagnostic): IteratorImportFix? =
            diagnostic.psiElement.safeAs<KtExpression>()?.let(::IteratorImportFix)
    }
}

internal open class ArrayAccessorImportFix(
    element: KtArrayAccessExpression,
    override val importNames: Collection<Name>
) : OrdinaryImportFixBase<KtArrayAccessExpression>(element, MyFactory) {

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.OPERATOR(it.arrayExpression!!) }

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        private fun importName(diagnostic: Diagnostic): Name {
            return when (diagnostic.factory) {
                Errors.NO_GET_METHOD -> OperatorNameConventions.GET
                Errors.NO_SET_METHOD -> OperatorNameConventions.SET
                else -> throw IllegalStateException("Shouldn't be called for other diagnostics")
            }
        }

        override fun createImportAction(diagnostic: Diagnostic): ArrayAccessorImportFix? {
            val factory = diagnostic.factory
            assert(factory == Errors.NO_GET_METHOD || factory == Errors.NO_SET_METHOD)

            val element = diagnostic.psiElement
            if (element is KtArrayAccessExpression && element.arrayExpression != null) {
                return ArrayAccessorImportFix(element, listOf(importName(diagnostic)))
            }

            return null
        }
    }
}

internal class DelegateAccessorsImportFix(
    element: KtExpression,
    override val importNames: Collection<Name>,
    private val solveSeveralProblems: Boolean,
) : OrdinaryImportFixBase<KtExpression>(element, MyFactory) {

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.DELEGATE(element)

    override fun createAction(editor: Editor, element: KtExpression, suggestions: Collection<FqName>): KotlinAddImportAction {
        if (solveSeveralProblems) {
            return createGroupedImportsAction(
                element.project, editor, element,
                KotlinBundle.message("fix.import.kind.delegate.accessors"),
                suggestions
            )
        }

        return super.createAction(editor, element, suggestions)
    }

    override fun calculateReceiverTypeFromDiagnostic(diagnostics: Collection<Diagnostic>): KotlinType? =
        diagnostics.firstOrNull { it.factory === Errors.DELEGATE_SPECIAL_FUNCTION_MISSING }
            ?.let {
                Errors.DELEGATE_SPECIAL_FUNCTION_MISSING.cast(it).b
            }

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        private fun importNames(diagnostics: Collection<Diagnostic>): Collection<Name> {
            return diagnostics.map {
                val missingMethodSignature =
                    if (it.factory === Errors.DELEGATE_SPECIAL_FUNCTION_MISSING) {
                        Errors.DELEGATE_SPECIAL_FUNCTION_MISSING.cast(it).a
                    } else {
                        Errors.DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE.cast(it).a
                    }
                if (missingMethodSignature.startsWith(OperatorNameConventions.GET_VALUE.identifier))
                    OperatorNameConventions.GET_VALUE
                else
                    OperatorNameConventions.SET_VALUE
            }.plus(OperatorNameConventions.PROVIDE_DELEGATE).distinct()
        }

        override fun createImportAction(diagnostic: Diagnostic): DelegateAccessorsImportFix? {
            val expression = diagnostic.psiElement as? KtExpression ?: return null
            val importNames = importNames(listOf(diagnostic))

            return DelegateAccessorsImportFix(expression, importNames, false)
        }

        override fun createImportActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<DelegateAccessorsImportFix> {
            val diagnostic = sameTypeDiagnostics.first()
            val element = diagnostic.psiElement
            val expression = element as? KtExpression ?: return emptyList()
            val names = importNames(sameTypeDiagnostics)
            return listOf(DelegateAccessorsImportFix(expression, names, true))
        }
    }
}

internal class ComponentsImportFix(
    element: KtExpression,
    override val importNames: Collection<Name>,
    private val solveSeveralProblems: Boolean
) : OrdinaryImportFixBase<KtExpression>(element, MyFactory) {

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.OPERATOR(it) }

    override fun createAction(editor: Editor, element: KtExpression, suggestions: Collection<FqName>): KotlinAddImportAction {
        if (solveSeveralProblems) {
            return createGroupedImportsAction(
                element.project, editor, element,
                KotlinBundle.message("fix.import.kind.component.functions"),
                suggestions
            )
        }

        return super.createAction(editor, element, suggestions)
    }

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        private fun importNames(diagnostics: Collection<Diagnostic>) =
            diagnostics.map { Name.identifier(Errors.COMPONENT_FUNCTION_MISSING.cast(it).a.identifier) }

        override fun createImportAction(diagnostic: Diagnostic) =
            (diagnostic.psiElement as? KtExpression)?.let {
                ComponentsImportFix(it, importNames(listOf(diagnostic)), false)
            }

        override fun createImportActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<ComponentsImportFix> {
            val element = sameTypeDiagnostics.first().psiElement
            val names = importNames(sameTypeDiagnostics)
            val solveSeveralProblems = sameTypeDiagnostics.size > 1
            val expression = element as? KtExpression ?: return emptyList()
            return listOf(ComponentsImportFix(expression, names, solveSeveralProblems))
        }
    }
}

internal open class ImportForMismatchingArgumentsFix(
    expression: KtSimpleNameExpression,
    expressionToAnalyze: KtExpression,
    factory: Factory
) : ImportFixBase<KtSimpleNameExpression>(expression, expressionToAnalyze, factory) {

    constructor(expression: KtSimpleNameExpression, factory: Factory):
        this(expression, expression, factory)

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.detect(it) }

    override val importNames = element?.mainReference?.resolvesByNames ?: emptyList()

    override fun elementsToCheckDiagnostics(): Collection<PsiElement> {
        val element = element ?: return emptyList()
        return when (val parent = element.parent) {
            is KtCallExpression -> parent.valueArguments +
                    parent.valueArguments.mapNotNull { it.getArgumentExpression() } +
                    parent.valueArguments.mapNotNull { it.getArgumentName()?.referenceExpression } +
                    listOfNotNull(parent.valueArgumentList, parent.referenceExpression(), parent.typeArgumentList)

            is KtBinaryExpression -> setOfNotNull(element, expressionToAnalyze)

            else -> emptyList()
        }
    }

    override fun fillCandidates(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        bindingContext: BindingContext,
        indicesHelper: KotlinIndicesHelper
    ): List<DeclarationDescriptor> {
        val element = element ?: return emptyList()

        if (!Name.isValidIdentifier(name)) return emptyList()
        val identifier = Name.identifier(name)

        val call = element.getParentCall(bindingContext) ?: return emptyList()
        val callElement = call.callElement as? KtExpression ?: return emptyList()
        if (callElement.anyDescendantOfType<PsiErrorElement>()) return emptyList() // incomplete call
        val elementToAnalyze = callElement.getQualifiedExpressionForSelectorOrThis()

        val file = element.containingKtFile
        val resolutionFacade = file.getResolutionFacade()
        val resolutionScope = elementToAnalyze.getResolutionScope(bindingContext, resolutionFacade)

        val imported = resolutionScope.collectFunctions(identifier, NoLookupLocation.FROM_IDE)

        fun filterFunctionMatchesAllArguments(original: MemberDescriptor): Boolean {
            // check that this function matches all arguments
            val resolutionScopeWithAddedImport = resolutionScope.addImportingScope(ExplicitImportsScope(listOf(original)))
            val dataFlowInfo = bindingContext.getDataFlowInfoBefore(elementToAnalyze)
            val newBindingContext = elementToAnalyze.analyzeInContext(
                resolutionScopeWithAddedImport,
                dataFlowInfo = dataFlowInfo,
                contextDependency = ContextDependency.DEPENDENT // to not check complete inference
            )
            return newBindingContext.diagnostics.none { it.severity == Severity.ERROR }
        }

        fun filterFunction(descriptor: FunctionDescriptor): Boolean {
            if (!callTypeAndReceiver.callType.descriptorKindFilter.accepts(descriptor)) return false

            val original = descriptor.original
            if (original in imported) return false // already imported

            return filterFunctionMatchesAllArguments(original)
        }

        val result = ArrayList<DeclarationDescriptor>()

        fun processDescriptor(descriptor: CallableDescriptor) {
            if (descriptor is FunctionDescriptor && filterFunction(descriptor)) {
                result.add(descriptor)
            }
        }

        ProgressManager.checkCanceled()

        (element.parent as? KtCallExpression)?.let { callExpression ->
            val filterByCallType: (DeclarationDescriptor) -> Boolean = callTypeAndReceiver.toFilter()
            indicesHelper.getClassesByName(callExpression, name).filterTo(result) { classDescriptor ->
                val original = classDescriptor.original
                filterFunctionMatchesAllArguments(original) && filterByCallType(classDescriptor)
            }
        }

        if (element.parent is KtBinaryExpression) {
            if (callTypeAndReceiver.callType == CallType.OPERATOR) {
                val type = (callTypeAndReceiver.receiver as? KtExpression)?.getCallableDescriptor()?.returnType ?: getReceiverTypeFromDiagnostic()
                if (type != null) {
                    result.addAll(indicesHelper.getCallableTopLevelExtensions(callTypeAndReceiver, listOf(type), { it == name }))
                }
            }
        }

        indicesHelper
            .getCallableTopLevelExtensions(callTypeAndReceiver, element, bindingContext, receiverTypeFromDiagnostic = null) { it == name }
            .forEach(::processDescriptor)

        if (!isSelectorInQualified(element)) {
            indicesHelper
                .getTopLevelCallablesByName(name)
                .forEach(::processDescriptor)
        }

        return result
    }

    companion object MyFactory : AbstractImportForMismatchingArgumentsFixFactory()

}

internal abstract class AbstractImportForMismatchingArgumentsFixFactory : ImportFixBase.Factory() {
    override fun createImportAction(diagnostic: Diagnostic): ImportForMismatchingArgumentsFix? {
        val element = diagnostic.psiElement
        val nameExpression =
            element.takeIf { it.elementType == KtNodeTypes.OPERATION_REFERENCE } as? KtSimpleNameExpression
            ?: element.getStrictParentOfType<KtCallExpression>()?.calleeExpression as? KtNameReferenceExpression
            ?: element.siblings(forward = false).firstOrNull{ it is KtSimpleNameExpression && it.elementType == KtNodeTypes.OPERATION_REFERENCE } as? KtSimpleNameExpression
            ?: (element as? KtBinaryExpression)?.operationReference
            ?: return null
        val expressionToAnalyze = element as? KtSimpleNameExpression ?: element as? KtBinaryExpression ?: nameExpression
        return ImportForMismatchingArgumentsFix(nameExpression, expressionToAnalyze, this)
    }
}

internal object ImportForMismatchingArgumentsFixFactoryWithUnresolvedReferenceQuickFix : AbstractImportForMismatchingArgumentsFixFactory(),
                                                                                         UnresolvedReferenceQuickFixFactory

internal object ImportForMissingOperatorFactory : ImportFixBase.Factory() {
    override fun createImportAction(diagnostic: Diagnostic): ImportFixBase<*>? {
        val element = diagnostic.psiElement as? KtExpression ?: return null
        val operatorDescriptor = Errors.OPERATOR_MODIFIER_REQUIRED.cast(diagnostic).a
        when (val name = operatorDescriptor.name) {
            OperatorNameConventions.GET, OperatorNameConventions.SET -> {
                if (element is KtArrayAccessExpression) {
                    return object : ArrayAccessorImportFix(element, listOf(name)) {
                        override val supportedErrors = setOf(Errors.OPERATOR_MODIFIER_REQUIRED)
                    }
                }
            }
        }

        return null
    }
}


private fun KotlinIndicesHelper.getClassesByName(expressionForPlatform: KtExpression, name: String): Collection<ClassDescriptor> =
    if (expressionForPlatform.containingKtFile.platform.isJvm()) {
        getJvmClassesByName(name)
    } else {
        val result = mutableListOf<ClassDescriptor>()
        val processor = Processors.cancelableCollectProcessor(result)
        // Enum entries should be contributed with members import fix
        processKotlinClasses(
            nameFilter = { it == name },
            // Enum entries should be contributed with members import fix
            psiFilter = { ktDeclaration -> ktDeclaration !is KtEnumEntry },
            kindFilter = { kind -> kind != ClassKind.ENUM_ENTRY },
            processor = processor::process
        )
        result
    }

/**
 * Collects classes and top-level type aliases from indices
 */
private fun KotlinIndicesHelper.getClassifiersByName(
    useSiteExpression: KtExpression,
    name: String,
): Collection<ClassifierDescriptor> = buildList {
    addAll(getClassesByName(useSiteExpression, name))

    processTopLevelTypeAliases({ it == name }, { add(it) })
}

private fun CallTypeAndReceiver<*, *>.toFilter() = { descriptor: DeclarationDescriptor ->
    callType.descriptorKindFilter.accepts(descriptor)
}

object AbstractImportFixInfo {
    @Volatile
    internal var IGNORE_MODULE_ERROR = false

    @TestOnly
    fun ignoreModuleError(disposable: Disposable) {
        IGNORE_MODULE_ERROR = true
        Disposer.register(disposable) { IGNORE_MODULE_ERROR = false }
    }

}