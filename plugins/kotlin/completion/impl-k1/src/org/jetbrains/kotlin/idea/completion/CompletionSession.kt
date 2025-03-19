// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.base.fe10.analysis.classId
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleOrigin
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.OriginCapability
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.util.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.base.util.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.util.getResolveScope
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.PreferKotlinClassesWeigher
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.platform.isMultiPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.denotedClassDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.util.match

class CompletionSessionConfiguration(
    val useBetterPrefixMatcherForNonImportedClasses: Boolean,
    val nonAccessibleDeclarations: Boolean,
    val javaGettersAndSetters: Boolean,
    val javaClassesNotToBeUsed: Boolean,
    val staticMembers: Boolean,
    val dataClassComponentFunctions: Boolean,
    val excludeEnumEntries: Boolean,
)

fun CompletionSessionConfiguration(parameters: CompletionParameters) = CompletionSessionConfiguration(
    useBetterPrefixMatcherForNonImportedClasses = parameters.invocationCount < 2,
    nonAccessibleDeclarations = parameters.invocationCount >= 2,
    javaGettersAndSetters = parameters.invocationCount >= 2,
    javaClassesNotToBeUsed = parameters.invocationCount >= 2,
    staticMembers = parameters.invocationCount >= 2,
    dataClassComponentFunctions = parameters.invocationCount >= 2,
    excludeEnumEntries = !parameters.position.languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries),
)

abstract class CompletionSession(
    protected val configuration: CompletionSessionConfiguration,
    originalParameters: CompletionParameters,
    resultSet: CompletionResultSet
) {
    init {
        CompletionBenchmarkSink.instance.onCompletionStarted(this)
    }

    protected val parameters = run {
        val fixedPosition = addParamTypesIfNeeded(originalParameters.position)
        originalParameters.withPosition(fixedPosition, fixedPosition.textOffset)
    }

    protected val toFromOriginalFileMapper = ToFromOriginalFileMapper.create(this.parameters)
    protected val position = this.parameters.position

    protected val file = position.containingFile as KtFile
    protected val resolutionFacade = file.getResolutionFacade()
    protected val moduleDescriptor = resolutionFacade.moduleDescriptor
    protected val project = position.project
    protected val isJvmModule = moduleDescriptor.platform.isJvm()
    protected val allowExpectedDeclarations = moduleDescriptor.platform.isMultiPlatform()
    protected val isDebuggerContext = file is KtCodeFragment

    protected val nameExpression: KtSimpleNameExpression?
    protected val expression: KtExpression?

    protected val applicabilityFilter: (DeclarationDescriptor) -> Boolean

    init {
        val reference = (position.parent as? KtSimpleNameExpression)?.mainReference
        if (reference != null) {
            if (reference.expression is KtLabelReferenceExpression) {
                this.nameExpression = null
                this.expression = reference.expression.parents.match(KtContainerNode::class, last = KtExpressionWithLabel::class)
            } else {
                this.nameExpression = reference.expression
                this.expression = nameExpression
            }
        } else {
            this.nameExpression = null
            this.expression = null
        }

        if (position.isInsideAnnotationEntryArgumentList()) {
            applicabilityFilter = { suggestDescriptorInsideAnnotationEntryArgumentList(it, expectedInfos) }
        } else {
            applicabilityFilter = { true }
        }
    }

    protected val bindingContext = CompletionBindingContextProvider.getInstance(project).getBindingContext(position, resolutionFacade)
    private val inDescriptor = position.getResolutionScope(bindingContext, resolutionFacade).ownerDescriptor

    protected val prefix = CompletionUtil.findIdentifierPrefix(
        originalParameters.position.containingFile,
        originalParameters.offset,
        kotlinIdentifierPartPattern(),
        kotlinIdentifierStartPattern()
    )

    protected val prefixMatcher = CamelHumpMatcher(prefix)

    protected val descriptorNameFilter: (String) -> Boolean = prefixMatcher.asStringNameFilter()

    protected val isVisibleFilter: (DeclarationDescriptor) -> Boolean =
        { isVisibleDescriptor(it, completeNonAccessible = configuration.nonAccessibleDeclarations) }
    protected val isVisibleFilterCheckAlways: (DeclarationDescriptor) -> Boolean =
        { isVisibleDescriptor(it, completeNonAccessible = false) }

    protected val referenceVariantsHelper = ReferenceVariantsHelper(
        bindingContext,
        resolutionFacade,
        moduleDescriptor,
        isVisibleFilter,
        NotPropertiesService.getNotProperties(position)
    )

    protected val shadowedFilter: ((Collection<DeclarationDescriptor>) -> Collection<DeclarationDescriptor>)? by lazy {
        ShadowedDeclarationsFilter.create(
            bindingContext = bindingContext,
            resolutionFacade = resolutionFacade,
            context = nameExpression!!,
            callTypeAndReceiver = callTypeAndReceiver,
        )?.createNonImportedDeclarationsFilter(
            importedDeclarations = referenceVariantsCollector!!.allCollected.imported,
            allowExpectedDeclarations = allowExpectedDeclarations,
        )
    }

    protected inline fun <reified T : DeclarationDescriptor> processWithShadowedFilter(descriptor: T, processor: (T) -> Unit) {
        val shadowedFilter = shadowedFilter
        val element = if (shadowedFilter != null) {
            shadowedFilter(listOf(descriptor)).singleOrNull()?.let { it as T }
        } else {
            descriptor
        }

        element?.let(processor)
    }

    protected val callTypeAndReceiver =
        if (nameExpression == null) CallTypeAndReceiver.UNKNOWN else CallTypeAndReceiver.detect(nameExpression)

    protected val receiverTypes: List<ReceiverType>? =
        nameExpression?.let { detectReceiverTypes(bindingContext, nameExpression, callTypeAndReceiver) }
            ?: (position.parent as? KDocName)?.let { detectReceiverTypesForKDocName(bindingContext, it) }


    protected val basicLookupElementFactory =
        BasicLookupElementFactory(project, InsertHandlerProvider(callTypeAndReceiver.callType, parameters.editor) { expectedInfos })

    // LookupElementsCollector instantiation is deferred because virtual call to createSorter uses data from derived classes
    protected val collector: LookupElementsCollector by lazy(LazyThreadSafetyMode.NONE) {
        LookupElementsCollector(
            { CompletionBenchmarkSink.instance.onFlush(this) },
            prefixMatcher, originalParameters, resultSet,
            createSorter(), (file as? KtCodeFragment)?.extraCompletionFilter,
            allowExpectedDeclarations,
        )
    }

    protected val searchScope: GlobalSearchScope =
        getResolveScope(originalParameters.originalFile as KtFile)

    protected fun indicesHelper(mayIncludeInaccessible: Boolean): KotlinIndicesHelper {
        val visibilityFilter = if (mayIncludeInaccessible) isVisibleFilter else isVisibleFilterCheckAlways
        return KotlinIndicesHelper(
            resolutionFacade,
            searchScope,
            visibilityFilter,
            applicabilityFilter = applicabilityFilter,
            filterOutPrivate = !mayIncludeInaccessible,
            declarationTranslator = { toFromOriginalFileMapper.toSyntheticFile(it) },
            file = file
        )
    }

    private fun isVisibleDescriptor(descriptor: DeclarationDescriptor, completeNonAccessible: Boolean): Boolean {
        if (!configuration.javaClassesNotToBeUsed && descriptor is ClassDescriptor) {
            if (descriptor.importableFqName?.isJavaClassNotToBeUsedInKotlin() == true) return false
        }

        if (descriptor is TypeParameterDescriptor && !isTypeParameterVisible(descriptor)) return false

        if (descriptor is DeclarationDescriptorWithVisibility) {
            val visible = descriptor.isVisible(position, callTypeAndReceiver.receiver as? KtExpression, bindingContext, resolutionFacade)
            if (visible) return true
            return completeNonAccessible && (!descriptor.isFromLibrary() || isDebuggerContext)
        }

        val fqName = descriptor.importableFqName
        return fqName == null || !fqName.isExcludedFromAutoImport(project, file)
    }

    private fun DeclarationDescriptor.isFromLibrary(): Boolean {
        if (module.getCapability(OriginCapability) == ModuleOrigin.LIBRARY) return true

        if (this is CallableMemberDescriptor && kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
            return overriddenDescriptors.all { it.isFromLibrary() }
        }

        return false
    }

    private fun isTypeParameterVisible(typeParameter: TypeParameterDescriptor): Boolean {
        val owner = typeParameter.containingDeclaration
        var parent: DeclarationDescriptor? = inDescriptor
        while (parent != null) {
            if (parent == owner) return true
            if (parent is ClassDescriptor && !parent.isInner) return false
            parent = parent.containingDeclaration
        }
        return true
    }

    protected fun flushToResultSet() {
        collector.flushToResultSet()
    }

    fun complete(): Boolean {
        return try {
            _complete().also {
                CompletionBenchmarkSink.instance.onCompletionEnded(this, false)
            }
        } catch (pce: ProcessCanceledException) {
            CompletionBenchmarkSink.instance.onCompletionEnded(this, true)
            throw pce
        }
    }

    private fun _complete(): Boolean {
        // we restart completion when prefix becomes "get" or "set" to ensure that properties get lower priority comparing to get/set functions (see KT-12299)
        val prefixPattern = StandardPatterns.string().with(object : PatternCondition<String>("get or set prefix") {
            override fun accepts(prefix: String, context: ProcessingContext?) = prefix == "get" || prefix == "set"
        })
        collector.restartCompletionOnPrefixChange(prefixPattern)

        val statisticsContext = calcContextForStatisticsInfo()
        if (statisticsContext != null) {
            collector.addLookupElementPostProcessor { lookupElement ->
                // we should put data into the original element because of DecoratorCompletionStatistician
                lookupElement.putUserDataDeep(STATISTICS_INFO_CONTEXT_KEY, statisticsContext)
                lookupElement
            }
        }

        doComplete()
        flushToResultSet()
        return !collector.isResultEmpty
    }

    fun addLookupElementPostProcessor(processor: (LookupElement) -> LookupElement) {
        collector.addLookupElementPostProcessor(processor)
    }

    protected abstract fun doComplete()

    protected abstract val descriptorKindFilter: DescriptorKindFilter?

    protected abstract val expectedInfos: Collection<ExpectedInfo>

    protected val importableFqNameClassifier = ImportableFqNameClassifier(file) {
        ImportInsertHelper.getInstance(file.project).isImportedWithDefault(ImportPath(it, false), file)
    }

    protected open fun createSorter(): CompletionSorter {
        var sorter = CompletionSorter.defaultSorter(parameters, prefixMatcher)!!

        sorter = sorter.weighBefore(
            "stats", DeprecatedWeigher, PriorityWeigher, PreferGetSetMethodsToPropertyWeigher,
            NotImportedWeigher(importableFqNameClassifier),
            NotImportedStaticMemberWeigher(importableFqNameClassifier),
            KindWeigher, CallableWeigher
        )

        sorter = sorter.weighAfter("stats", VariableOrFunctionWeigher, ImportedWeigher(importableFqNameClassifier))

        val preferContextElementsWeigher = PreferContextElementsWeigher(inDescriptor)
        sorter =
            if (callTypeAndReceiver is CallTypeAndReceiver.SUPER_MEMBERS) { // for completion after "super." strictly prefer the current member
                sorter.weighBefore("kotlin.deprecated", preferContextElementsWeigher)
            } else {
                sorter.weighBefore("kotlin.proximity", preferContextElementsWeigher)
            }

        sorter = sorter.weighBefore("middleMatching", PreferMatchingItemWeigher)

        // we insert one more RealPrefixMatchingWeigher because one inserted in default sorter is placed in a bad position (after "stats")
        sorter = sorter.weighAfter("lift.shorter", RealPrefixMatchingWeigher())

        sorter = sorter.weighAfter(
            "kotlin.proximity",
            ByNameAlphabeticalWeigher,
            PreferKotlinClassesWeigher.Weigher,
            PreferLessParametersWeigher
        )

        sorter = sorter.weighBefore("prefix", K1SoftDeprecationWeigher)

        sorter = if (expectedInfos.all { it.fuzzyType?.type?.isUnit() == true }) {
            sorter.weighBefore("prefix", PreferDslMembers)
        } else {
            sorter.weighAfter("kotlin.preferContextElements", PreferDslMembers)
        }

        return sorter
    }

    private fun calcContextForStatisticsInfo(): String? {
        if (expectedInfos.isEmpty()) return null

        var context = expectedInfos
            .mapNotNull { it.fuzzyType?.type?.constructor?.declarationDescriptor?.importableFqName }
            .distinct()
            .singleOrNull()
            ?.let { "expectedType=$it" }

        if (context == null) {
            context = expectedInfos
                .mapNotNull { it.expectedName }
                .distinct()
                .singleOrNull()
                ?.let { "expectedName=$it" }
        }

        return context
    }

    protected val referenceVariantsCollector = if (nameExpression != null) {
        ReferenceVariantsCollector(
            referenceVariantsHelper = referenceVariantsHelper,
            indicesHelper = indicesHelper(true),
            prefixMatcher = prefixMatcher,
            applicabilityFilter = applicabilityFilter,
            nameExpression = nameExpression,
            callTypeAndReceiver = callTypeAndReceiver,
            resolutionFacade = resolutionFacade,
            bindingContext = bindingContext,
            importableFqNameClassifier = importableFqNameClassifier,
            configuration = configuration,
            allowExpectedDeclarations = allowExpectedDeclarations,
        )
    } else {
        null
    }

    protected fun ReferenceVariants.excludeNonInitializedVariable(): ReferenceVariants {
        return ReferenceVariants(referenceVariantsHelper.excludeNonInitializedVariable(imported, position), notImportedExtensions)
    }

    protected fun referenceVariantsWithSingleFunctionTypeParameter(): ReferenceVariants? {
        val variants = referenceVariantsCollector?.allCollected ?: return null
        val filter = { descriptor: DeclarationDescriptor ->
            descriptor is FunctionDescriptor && LookupElementFactory.hasSingleFunctionTypeParameter(descriptor)
        }
        return ReferenceVariants(variants.imported.filter(filter), variants.notImportedExtensions.filter(filter))
    }

    protected fun getRuntimeReceiverTypeReferenceVariants(lookupElementFactory: LookupElementFactory): Pair<ReferenceVariants, LookupElementFactory>? {
        val evaluator = file.getCopyableUserData(CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR) ?: return null
        val referenceVariants = referenceVariantsCollector?.allCollected ?: return null

        val explicitReceiver = callTypeAndReceiver.receiver as? KtExpression ?: return null
        val type = bindingContext.getType(explicitReceiver) ?: return null
        if (!TypeUtils.canHaveSubtypes(KotlinTypeChecker.DEFAULT, type)) return null

        val runtimeType = evaluator(explicitReceiver)
        if (runtimeType == null || runtimeType == type) return null

        val expressionReceiver = ExpressionReceiver.create(explicitReceiver, runtimeType, bindingContext)
        val (variants, notImportedExtensions) = ReferenceVariantsCollector(
            referenceVariantsHelper = referenceVariantsHelper,
            indicesHelper = indicesHelper(true),
            prefixMatcher = prefixMatcher,
            applicabilityFilter = applicabilityFilter,
            nameExpression = nameExpression!!,
            callTypeAndReceiver = callTypeAndReceiver,
            resolutionFacade = resolutionFacade,
            bindingContext = bindingContext,
            importableFqNameClassifier = importableFqNameClassifier,
            configuration = configuration,
            allowExpectedDeclarations = allowExpectedDeclarations,
            runtimeReceiver = expressionReceiver,
        ).collectReferenceVariants(descriptorKindFilter!!)

        val filteredVariants = filterVariantsForRuntimeReceiverType(variants, referenceVariants.imported)
        val filteredNotImportedExtensions =
            filterVariantsForRuntimeReceiverType(notImportedExtensions, referenceVariants.notImportedExtensions)

        val runtimeVariants = ReferenceVariants(filteredVariants, filteredNotImportedExtensions)
        return Pair(runtimeVariants, lookupElementFactory.copy(receiverTypes = listOf(ReceiverType(runtimeType, 0))))
    }

    private fun <TDescriptor : DeclarationDescriptor> filterVariantsForRuntimeReceiverType(
        runtimeVariants: Collection<TDescriptor>,
        baseVariants: Collection<TDescriptor>
    ): Collection<TDescriptor> {
        val baseVariantsByName = baseVariants.groupBy { it.name }
        val result = ArrayList<TDescriptor>()
        for (variant in runtimeVariants) {
            val candidates = baseVariantsByName[variant.name]
            if (candidates == null || candidates.none { compareDescriptors(project, variant, it) }) {
                result.add(variant)
            }
        }
        return result
    }

    protected open fun shouldCompleteTopLevelCallablesFromIndex(): Boolean {
        if (nameExpression == null) return false
        if ((descriptorKindFilter?.kindMask ?: 0).and(DescriptorKindFilter.CALLABLES_MASK) == 0) return false
        if (callTypeAndReceiver is CallTypeAndReceiver.IMPORT_DIRECTIVE) return false
        return callTypeAndReceiver.receiver == null
    }

    protected fun processTopLevelCallables(processor: (DeclarationDescriptor) -> Unit) {
        indicesHelper(true).processTopLevelCallables({ prefixMatcher.prefixMatches(it) }) {
            processWithShadowedFilter(it, processor)
        }
    }

    protected fun withCollectRequiredContextVariableTypes(action: (LookupElementFactory) -> Unit): Collection<FuzzyType> {
        val provider = CollectRequiredTypesContextVariablesProvider()
        val lookupElementFactory = createLookupElementFactory(provider)
        action(lookupElementFactory)
        return provider.requiredTypes
    }

    protected fun withContextVariablesProvider(contextVariablesProvider: ContextVariablesProvider, action: (LookupElementFactory) -> Unit) {
        val lookupElementFactory = createLookupElementFactory(contextVariablesProvider)
        action(lookupElementFactory)
    }

    protected open fun createLookupElementFactory(contextVariablesProvider: ContextVariablesProvider): LookupElementFactory {
        return LookupElementFactory(
            basicLookupElementFactory, parameters.editor, receiverTypes,
            callTypeAndReceiver.callType, inDescriptor, contextVariablesProvider
        )
    }

    protected fun detectReceiverTypes(
        bindingContext: BindingContext,
        nameExpression: KtSimpleNameExpression,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>
    ): List<ReceiverType>? {
        var receiverTypes = callTypeAndReceiver.receiverTypesWithIndex(
            bindingContext, nameExpression, moduleDescriptor, resolutionFacade,
            stableSmartCastsOnly = true, /* we don't include smart cast receiver types for "unstable" receiver value to mark members grayed */
            withImplicitReceiversWhenExplicitPresent = true
        )

        if (callTypeAndReceiver is CallTypeAndReceiver.SAFE || isDebuggerContext) {
            receiverTypes = receiverTypes?.map { ReceiverType(it.type.makeNotNullable(), it.receiverIndex) }
        }

        return receiverTypes
    }

    private fun detectReceiverTypesForKDocName(
        context: BindingContext,
        kDocName: KDocName,
    ): List<ReceiverType>? {
        val kDocLink = kDocName.getStrictParentOfType<KDocLink>() ?: return null
        val kDocOwner = kDocName.getContainingDoc().getOwner()
        val kDocOwnerDescriptor = kDocOwner?.resolveToDescriptorIfAny() ?: return null

        return resolveKDocLink(context, resolutionFacade, kDocOwnerDescriptor, kDocLink, kDocLink.getTagIfSubject(), kDocLink.qualifier)
            .filterIsInstance<ClassifierDescriptorWithTypeParameters>()
            .mapNotNull { it.denotedClassDescriptor }
            .flatMap { listOfNotNull(it, it.companionObjectDescriptor) }
            .map { ReceiverType(it.defaultType, receiverIndex = 0) }
    }

    companion object {
        private fun suggestDescriptorInsideAnnotationEntryArgumentList(
            descriptor: DeclarationDescriptor,
            expectedInfos: Collection<ExpectedInfo>,
        ): Boolean {
            if (descriptor.annotations.any { it.classId == StandardClassIds.Annotations.IntrinsicConstEvaluation }) return true

            if (descriptor !is CallableDescriptor) return true

            return when (descriptor) {
                is VariableDescriptor -> descriptor.isConst
                is FunctionDescriptor -> {
                    if (descriptor.fqNameOrNull() !in ArrayFqNames.ARRAY_CALL_FQ_NAMES) return false

                    val fuzzyType = descriptor.returnType?.toFuzzyType(descriptor.typeParameters) ?: return false
                    expectedInfos.isEmpty() || expectedInfos.any { it.fuzzyType?.checkIsSubtypeOf(fuzzyType) != null }
                }

                else -> false
            }
        }
    }
}
