// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.ImportFilter
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.hint.HintManager
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
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.elementType
import com.intellij.util.Processors
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.actions.*
import org.jetbrains.kotlin.idea.actions.createGroupedImportsAction
import org.jetbrains.kotlin.idea.actions.createSingleImportAction
import org.jetbrains.kotlin.idea.actions.createSingleImportActionForConstructor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.getResolveScope
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeInsight.KotlinAutoImportsFilter
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.UnresolvedReferenceQuickFixFactory
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.imports.canBeReferencedViaImport
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.getCallableDescriptor
import org.jetbrains.kotlin.idea.intentions.singleLambdaArgumentExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.parentOrNull
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
import java.util.TreeSet

/**
 * Check possibility and perform fix for unresolved references.
 */
internal abstract class ImportFixBase<T : KtExpression> protected constructor(
    expression: T,
    factory: Factory
) : KotlinQuickFixAction<T>(expression), HintAction, HighPriorityAction {

    private val project = expression.project

    private val modificationCountOnCreate = PsiModificationTracker.getInstance(project).modificationCount

    protected lateinit var suggestions: Collection<FqName>

    @IntentionName
    private lateinit var text: String

    internal fun computeSuggestions() {
        val suggestionDescriptors = collectSuggestionDescriptors()
        suggestions = collectSuggestions(suggestionDescriptors)
        text = calculateText(suggestionDescriptors)
    }

    internal fun suggestions() = suggestions

    protected open val supportedErrors = factory.supportedErrors.toSet()

    protected abstract val importNames: Collection<Name>
    protected abstract fun getCallTypeAndReceiver(): CallTypeAndReceiver<*, *>?
    protected open fun getReceiverTypeFromDiagnostic(): KotlinType? = null

    override fun showHint(editor: Editor): Boolean {
        val element = element?.takeIf(PsiElement::isValid) ?: return false

        if (isOutdated()) return false

        if (ApplicationManager.getApplication().isHeadlessEnvironment ||
            !DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled ||
            HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        if (suggestions.isEmpty()) return false

        return createAction(project, editor, element).showHint()
    }

    @IntentionName
    private fun calculateText(suggestionDescriptors: Collection<DeclarationDescriptor>): String {
        val descriptors  =
            suggestionDescriptors.mapTo(hashSetOf()) { it.original }

        val ktFile = element?.containingKtFile ?: return KotlinBundle.message("fix.import")
        val languageVersionSettings = ktFile.languageVersionSettings
        val prioritizer = Prioritizer(ktFile)

        val kindNameGroupedByKind = descriptors.mapNotNull { descriptor ->
            val kind = when {
                descriptor.isExtensionProperty -> ImportKind.EXTENSION_PROPERTY
                descriptor is PropertyDescriptor -> ImportKind.PROPERTY
                descriptor is ClassConstructorDescriptor -> ImportKind.CLASS
                descriptor is FunctionDescriptor && descriptor.isExtension -> ImportKind.EXTENSION_FUNCTION
                descriptor is FunctionDescriptor -> ImportKind.FUNCTION
                DescriptorUtils.isObject(descriptor) -> ImportKind.OBJECT
                descriptor is ClassDescriptor -> ImportKind.CLASS
                else -> null
            } ?: return@mapNotNull null

            val name = buildString {
                descriptor.safeAs<CallableDescriptor>()?.let { callableDescriptor ->
                    val extensionReceiverParameter = callableDescriptor.extensionReceiverParameter
                    if (extensionReceiverParameter != null) {
                        extensionReceiverParameter.type.constructor.declarationDescriptor.safeAs<ClassDescriptor>()?.name?.let {
                            append(it.asString())
                        }
                    } else {
                        callableDescriptor.containingDeclaration.safeAs<ClassDescriptor>()?.name?.let {
                            append(it.asString())
                        }
                    }
                }

                descriptor.name.takeUnless { it.isSpecial }?.let {
                    if (this.isNotEmpty()) append('.')
                    append(it.asString())
                }
            }
            ImportName(kind, name, prioritizer.priority(descriptor, languageVersionSettings))
        }.groupBy(keySelector = { it.kind }) { it }

        return if (kindNameGroupedByKind.size == 1) {
            val (kind, names) = kindNameGroupedByKind.entries.first()
            val sortedNames = TreeSet<ImportName>(compareBy({ it.priority }, { it.kind }, { it.name }))
            sortedNames.addAll(names)
            val firstName = sortedNames.first().name
            val singlePackage = suggestions.groupBy { it.parentOrNull() ?: FqName.ROOT }.size == 1

            if (singlePackage) {
                val size = sortedNames.size
                if (size == 2) {
                    KotlinBundle.message("fix.import.kind.0.name.1.and.name.2", kind.toText(size), firstName, sortedNames.last().name)
                } else {
                    KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(size), firstName, size - 1)
                }
            } else if (kind.groupedByPackage) {
                KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), firstName, 0)
            } else {
                val groupBy = sortedNames.map { it.name }.toSortedSet().groupBy { it.substringBefore('.') }
                val value = groupBy.entries.first().value
                val first = value.first()
                val multiple = if (value.size == 1) 0 else 1
                when {
                    groupBy.size != 1 -> KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), first.substringAfter('.'), multiple)
                    value.size == 2 -> KotlinBundle.message("fix.import.kind.0.name.1.and.name.2", kind.toText(value.size), first, value.last())
                    else -> KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), first, multiple)
                }
            }
        } else {
            KotlinBundle.message("fix.import")
        }
    }

    private class ImportName(val kind: ImportKind, val name: String, val priority: ComparablePriority)

    private enum class ImportKind(private val key: String, val groupedByPackage: Boolean = false) {
        CLASS("text.class.0", true),
        PROPERTY("text.property.0"),
        OBJECT("text.object.0", true),
        FUNCTION("text.function.0"),
        EXTENSION_PROPERTY("text.extension.property.0"),
        EXTENSION_FUNCTION("text.extension.function.0");

        fun toText(number: Int) = KotlinBundle.message(key, if (number == 1) 1 else 2)
    }

    override fun getText(): String = text

    override fun getFamilyName() = KotlinBundle.message("fix.import")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return element != null && suggestions.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        CommandProcessor.getInstance().runUndoTransparentAction {
            createAction(project, editor!!, element).execute()
        }
    }

    override fun startInWriteAction() = false

    private fun isOutdated() = modificationCountOnCreate != PsiModificationTracker.getInstance(project).modificationCount

    open fun createAction(project: Project, editor: Editor, element: KtExpression): KotlinAddImportAction {
        return createSingleImportAction(project, editor, element, suggestions)
    }

    private fun createActionWithAutoImportsFilter(project: Project, editor: Editor, element: KtExpression): KotlinAddImportAction {
        val filteredSuggestions =
            KotlinAutoImportsFilter.filterSuggestionsIfApplicable(element.containingKtFile, suggestions)

        return createSingleImportAction(project, editor, element, filteredSuggestions)
    }

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
        val nameStr = name.asString()
        if (nameStr.isEmpty()) return emptyList()

        val file = element.containingKtFile

        val bindingContext = element.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
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

internal abstract class OrdinaryImportFixBase<T : KtExpression>(expression: T, factory: Factory) : ImportFixBase<T>(expression, factory) {
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

                indicesHelper.getClassesByName(expression, name).filterTo(result, filterByCallType)

                indicesHelper.processTopLevelTypeAliases({ it == name }, {
                    if (filterByCallType(it)) {
                        result.add(it)
                    }
                })

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
internal abstract class AbstractImportFix(expression: KtSimpleNameExpression, factory: Factory) :
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
        // TODO Type-aliases
        return indicesHelper.getClassesByName(expression, name)
            .asSequence()
            .map { it.constructors }.flatten()
            .filter { it.importableFqName != null }
            .filter(filterByCallType)
            .toList()
    }

    override fun createAction(project: Project, editor: Editor, element: KtExpression): KotlinAddImportAction {
        return createSingleImportActionForConstructor(project, editor, element, suggestions)
    }

    override val importNames = element?.mainReference?.resolvesByNames ?: emptyList()

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        override fun createImportAction(diagnostic: Diagnostic) =
            diagnostic.psiElement.safeAs<KtSimpleNameExpression>()?.let(::ImportConstructorReferenceFix)
    }
}

internal class InvokeImportFix(
    expression: KtExpression, val diagnostic: Diagnostic
) : OrdinaryImportFixBase<KtExpression>(expression, MyFactory) {
    override val importNames = listOf(OperatorNameConventions.INVOKE)

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.OPERATOR(it) }

    override fun getReceiverTypeFromDiagnostic(): KotlinType = Errors.FUNCTION_EXPECTED.cast(diagnostic).b

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        override fun createImportAction(diagnostic: Diagnostic) =
            diagnostic.psiElement.safeAs<KtExpression>()?.let {
                InvokeImportFix(it, diagnostic)
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
    private val diagnostic: Diagnostic,
) : OrdinaryImportFixBase<KtExpression>(element, MyFactory) {

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.DELEGATE(element)

    override fun createAction(project: Project, editor: Editor, element: KtExpression): KotlinAddImportAction {
        if (solveSeveralProblems) {
            return createGroupedImportsAction(
                project, editor, element,
                KotlinBundle.message("fix.import.kind.delegate.accessors"),
                suggestions
            )
        }

        return super.createAction(project, editor, element)
    }

    override fun getReceiverTypeFromDiagnostic(): KotlinType? =
        if (diagnostic.factory === Errors.DELEGATE_SPECIAL_FUNCTION_MISSING) Errors.DELEGATE_SPECIAL_FUNCTION_MISSING.cast(diagnostic).b else null

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

            return DelegateAccessorsImportFix(expression, importNames, false, diagnostic)
        }

        override fun createImportActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<DelegateAccessorsImportFix> {
            val diagnostic = sameTypeDiagnostics.first()
            val element = diagnostic.psiElement
            val expression = element as? KtExpression ?: return emptyList()
            val names = importNames(sameTypeDiagnostics)
            return listOf(DelegateAccessorsImportFix(expression, names, true, diagnostic))
        }
    }
}

internal class ComponentsImportFix(
    element: KtExpression,
    override val importNames: Collection<Name>,
    private val solveSeveralProblems: Boolean
) : OrdinaryImportFixBase<KtExpression>(element, MyFactory) {

    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.OPERATOR(it) }

    override fun createAction(project: Project, editor: Editor, element: KtExpression): KotlinAddImportAction {
        if (solveSeveralProblems) {
            return createGroupedImportsAction(
                project, editor, element,
                KotlinBundle.message("fix.import.kind.component.functions"),
                suggestions
            )
        }

        return super.createAction(project, editor, element)
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
    expression: KtSimpleNameExpression, factory: Factory
) : ImportFixBase<KtSimpleNameExpression>(expression, factory) {
    override fun getCallTypeAndReceiver() = element?.let { CallTypeAndReceiver.detect(it) }

    override val importNames = element?.mainReference?.resolvesByNames ?: emptyList()

    override fun elementsToCheckDiagnostics(): Collection<PsiElement> {
        val element = element ?: return emptyList()
        return when (val parent = element.parent) {
            is KtCallExpression -> parent.valueArguments +
                    parent.valueArguments.mapNotNull { it.getArgumentExpression() } +
                    parent.valueArguments.mapNotNull { it.getArgumentName()?.referenceExpression } +
                    listOfNotNull(parent.valueArgumentList, parent.referenceExpression(), parent.typeArgumentList)
            is KtBinaryExpression -> listOf(element)
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

        fun filterFunction(descriptor: FunctionDescriptor): Boolean {
            if (!callTypeAndReceiver.callType.descriptorKindFilter.accepts(descriptor)) return false

            val original = descriptor.original
            if (original in imported) return false // already imported

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

        val result = ArrayList<FunctionDescriptor>()

        fun processDescriptor(descriptor: CallableDescriptor) {
            if (descriptor is FunctionDescriptor && filterFunction(descriptor)) {
                result.add(descriptor)
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
        val nameExpression = element.takeIf { it.elementType == KtNodeTypes.OPERATION_REFERENCE }.safeAs<KtSimpleNameExpression>()
            ?: element.getStrictParentOfType<KtCallExpression>()?.calleeExpression?.safeAs<KtNameReferenceExpression>()
            ?: return null
        return ImportForMismatchingArgumentsFix(nameExpression, this)
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