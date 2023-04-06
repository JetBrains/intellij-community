// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.module.Module
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.util.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.completion.implCommon.keywords.BreakContinueKeywordHandler
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.StringTemplateCompletion.checkQualifiedThisInStringTemplateCompletion
import org.jetbrains.kotlin.idea.completion.keywords.DefaultCompletionKeywordHandlerProvider
import org.jetbrains.kotlin.idea.completion.keywords.createLookups
import org.jetbrains.kotlin.idea.completion.smart.*
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.ExplicitImportsScope
import org.jetbrains.kotlin.resolve.scopes.utils.addImportingScope
import org.jetbrains.kotlin.util.kind
import org.jetbrains.kotlin.util.supertypesWithAny
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class BasicCompletionSession(
    configuration: CompletionSessionConfiguration,
    parameters: CompletionParameters,
    resultSet: CompletionResultSet,
) : CompletionSession(configuration, parameters, resultSet) {

    private interface CompletionKind {
        val descriptorKindFilter: DescriptorKindFilter?
        fun doComplete()
        fun shouldDisableAutoPopup() = false
        fun addWeighers(sorter: CompletionSorter) = sorter
    }

    private val completionKind by lazy { detectCompletionKind() }

    override val descriptorKindFilter: DescriptorKindFilter? get() = completionKind.descriptorKindFilter

    private val smartCompletion = expression?.let {
        SmartCompletion(
            expression = it,
            resolutionFacade = resolutionFacade,
            bindingContext = bindingContext,
            moduleDescriptor = moduleDescriptor,
            visibilityFilter = isVisibleFilter,
            indicesHelper = indicesHelper(false),
            prefixMatcher = prefixMatcher,
            inheritorSearchScope = GlobalSearchScope.EMPTY_SCOPE,
            toFromOriginalFileMapper = toFromOriginalFileMapper,
            callTypeAndReceiver = callTypeAndReceiver,
            isJvmModule = isJvmModule,
            forBasicCompletion = true,
        )
    }

    override val expectedInfos: Collection<ExpectedInfo> get() = smartCompletion?.expectedInfos ?: emptyList()

    private fun detectCompletionKind(): CompletionKind {
        if (nameExpression == null) {
            return if ((position.parent as? KtNamedDeclaration)?.nameIdentifier == position) DECLARATION_NAME else KEYWORDS_ONLY
        }

        if (OPERATOR_NAME.isApplicable()) {
            return OPERATOR_NAME
        }

        if (NamedArgumentCompletion.isOnlyNamedArgumentExpected(nameExpression, resolutionFacade)) {
            return NAMED_ARGUMENTS_ONLY
        }

        if (nameExpression.getStrictParentOfType<KtSuperExpression>() != null) {
            return SUPER_QUALIFIER
        }

        return ALL
    }

    fun shouldDisableAutoPopup(): Boolean = completionKind.shouldDisableAutoPopup()

    override fun shouldCompleteTopLevelCallablesFromIndex() = super.shouldCompleteTopLevelCallablesFromIndex() && prefix.isNotEmpty()

    override fun doComplete() {
        assert(parameters.completionType == CompletionType.BASIC)

        if (parameters.isAutoPopup) {
            collector.addLookupElementPostProcessor { lookupElement ->
                lookupElement.putUserData(LookupCancelService.AUTO_POPUP_AT, position.startOffset)
                lookupElement
            }

            if (isInFunctionLiteralStart(position)) {
                collector.addLookupElementPostProcessor { lookupElement ->
                    lookupElement.putUserData(KotlinCompletionCharFilter.SUPPRESS_ITEM_SELECTION_BY_CHARS_ON_TYPING, Unit)
                    lookupElement
                }
            }
        }

        collector.addLookupElementPostProcessor { lookupElement ->
            position.argList?.let { lookupElement.argList = it }
            lookupElement
        }

        completionKind.doComplete()
    }

    private fun isInFunctionLiteralStart(position: PsiElement): Boolean {
        var prev = position.prevLeaf { it !is PsiWhiteSpace && it !is PsiComment }
        if (prev?.node?.elementType == KtTokens.LPAR) {
            prev = prev?.prevLeaf { it !is PsiWhiteSpace && it !is PsiComment }
        }
        if (prev?.node?.elementType != KtTokens.LBRACE) return false
        val functionLiteral = prev!!.parent as? KtFunctionLiteral ?: return false
        return functionLiteral.lBrace == prev
    }

    override fun createSorter(): CompletionSorter {
        var sorter = super.createSorter()

        if (smartCompletion != null) {
            val smartCompletionInBasicWeigher = SmartCompletionInBasicWeigher(
                smartCompletion,
                callTypeAndReceiver,
                resolutionFacade,
                bindingContext,
            )

            sorter = sorter.weighBefore(
                KindWeigher.toString(),
                smartCompletionInBasicWeigher,
                CallableReferenceWeigher(callTypeAndReceiver.callType),
            )
        }

        sorter = completionKind.addWeighers(sorter)

        return sorter
    }

    private val ALL = object : CompletionKind {
        override val descriptorKindFilter: DescriptorKindFilter by lazy {
            callTypeAndReceiver.callType.descriptorKindFilter.let { filter ->
                filter.takeIf { it.kindMask.and(DescriptorKindFilter.PACKAGES_MASK) != 0 }
                    ?.exclude(DescriptorKindExclude.TopLevelPackages)
                    ?: filter
            }
        }

        override fun shouldDisableAutoPopup(): Boolean =
            isStartOfExtensionReceiverFor() is KtProperty && wasAutopopupRecentlyCancelled(parameters)

        override fun doComplete() {
            val declaration = isStartOfExtensionReceiverFor()
            if (declaration != null) {
                completeDeclarationNameFromUnresolvedOrOverride(declaration)

                if (declaration is KtProperty) {
                    // we want to insert type only if the property is lateinit,
                    // because lateinit var cannot have its type deduced from initializer
                    completeParameterOrVarNameAndType(withType = declaration.hasModifier(KtTokens.LATEINIT_KEYWORD))
                }

                // no auto-popup on typing after "val", "var" and "fun" because it's likely the name of the declaration which is being typed by user
                if (parameters.invocationCount == 0 && (
                            // suppressOtherCompletion
                            declaration !is KtNamedFunction && declaration !is KtProperty ||
                                    prefixMatcher.prefix.let { it.isEmpty() || it[0].isLowerCase() /* function name usually starts with lower case letter */ }
                            )
                ) {
                    if (declaration is KtNamedFunction &&
                        declaration.modifierList?.allChildren.orEmpty()
                            .map { it.node.elementType }
                            .none { it is KtModifierKeywordToken && it !in KtTokens.VISIBILITY_MODIFIERS }
                    ) {
                        KEYWORDS_ONLY.doComplete()
                    }
                    return
                }
            }

            fun completeWithSmartCompletion(lookupElementFactory: LookupElementFactory) {
                if (smartCompletion != null) {
                    val (additionalItems, @Suppress("UNUSED_VARIABLE") inheritanceSearcher) = smartCompletion.additionalItems(
                        lookupElementFactory
                    )

                    // all additional items should have SMART_COMPLETION_ITEM_PRIORITY_KEY to be recognized by SmartCompletionInBasicWeigher
                    for (item in additionalItems) {
                        if (item.getUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY) == null) {
                            item.putUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY, SmartCompletionItemPriority.DEFAULT)
                        }
                    }

                    collector.addElements(additionalItems)
                }
            }

            withCollectRequiredContextVariableTypes { lookupFactory ->
                DslMembersCompletion(
                    prefixMatcher,
                    lookupFactory,
                    receiverTypes,
                    collector,
                    indicesHelper(true),
                    callTypeAndReceiver,
                ).completeDslFunctions()
            }

            KEYWORDS_ONLY.doComplete()

            val contextVariableTypesForSmartCompletion = withCollectRequiredContextVariableTypes(::completeWithSmartCompletion)
            val contextVariableTypesForReferenceVariants = withCollectRequiredContextVariableTypes { lookupElementFactory ->
                when {
                    prefix.isEmpty() ||
                            callTypeAndReceiver.receiver != null ||
                            CodeInsightSettings.getInstance().completionCaseSensitive == CodeInsightSettings.NONE
                    -> {
                        addReferenceVariantElements(lookupElementFactory, descriptorKindFilter)
                    }

                    prefix[0].isLowerCase() -> {
                        addReferenceVariantElements(lookupElementFactory, USUALLY_START_LOWER_CASE.intersect(descriptorKindFilter))
                        addReferenceVariantElements(lookupElementFactory, USUALLY_START_UPPER_CASE.intersect(descriptorKindFilter))
                    }

                    else -> {
                        addReferenceVariantElements(lookupElementFactory, USUALLY_START_UPPER_CASE.intersect(descriptorKindFilter))
                        addReferenceVariantElements(lookupElementFactory, USUALLY_START_LOWER_CASE.intersect(descriptorKindFilter))
                    }
                }

                referenceVariantsCollector!!.collectingFinished()
            }

            // getting root packages from scope is very slow so we do this in alternative way
            if (callTypeAndReceiver.receiver == null &&
                callTypeAndReceiver.callType.descriptorKindFilter.kindMask.and(DescriptorKindFilter.PACKAGES_MASK) != 0
            ) {
                //TODO: move this code somewhere else?
                val packageNames = KotlinPackageIndexUtils.getSubPackageFqNames(FqName.ROOT, searchScope, prefixMatcher.asNameFilter())
                    .toHashSet()

                if ((parameters.originalFile as KtFile).platform.isJvm()) {
                    JavaPsiFacade.getInstance(project).findPackage("")?.getSubPackages(searchScope)?.forEach { psiPackage ->
                        val name = psiPackage.name
                        if (Name.isValidIdentifier(name!!)) {
                            packageNames.add(FqName(name))
                        }
                    }
                }

                packageNames.forEach { collector.addElement(basicLookupElementFactory.createLookupElementForPackage(it)) }
            }

            flushToResultSet()

            NamedArgumentCompletion.complete(collector, expectedInfos, callTypeAndReceiver.callType)
            flushToResultSet()

            val contextVariablesProvider = RealContextVariablesProvider(referenceVariantsHelper, position)
            withContextVariablesProvider(contextVariablesProvider) { lookupElementFactory ->
                if (receiverTypes != null) {
                    ExtensionFunctionTypeValueCompletion(receiverTypes, callTypeAndReceiver.callType, lookupElementFactory)
                        .processVariables(contextVariablesProvider)
                        .forEach {
                            val lookupElements = it.factory.createStandardLookupElementsForDescriptor(
                                it.invokeDescriptor,
                                useReceiverTypes = true,
                            )

                            collector.addElements(lookupElements)
                        }
                }

                if (contextVariableTypesForSmartCompletion.any { contextVariablesProvider.functionTypeVariables(it).isNotEmpty() }) {
                    completeWithSmartCompletion(lookupElementFactory)
                }

                if (contextVariableTypesForReferenceVariants.any { contextVariablesProvider.functionTypeVariables(it).isNotEmpty() }) {
                    val (imported, notImported) = referenceVariantsWithSingleFunctionTypeParameter()!!
                    collector.addDescriptorElements(imported, lookupElementFactory)
                    collector.addDescriptorElements(notImported, lookupElementFactory, notImported = true)
                }

                val staticMembersCompletion = StaticMembersCompletion(
                    prefixMatcher,
                    resolutionFacade,
                    lookupElementFactory,
                    referenceVariantsCollector!!.allCollected.imported,
                    isJvmModule
                )

                if (callTypeAndReceiver is CallTypeAndReceiver.DEFAULT) {
                    staticMembersCompletion.completeFromImports(file, collector)
                }

                completeNonImported(lookupElementFactory)
                flushToResultSet()

                if (isDebuggerContext) {
                    val variantsAndFactory = getRuntimeReceiverTypeReferenceVariants(lookupElementFactory)
                    if (variantsAndFactory != null) {
                        val variants = variantsAndFactory.first
                        val resultLookupElementFactory = variantsAndFactory.second
                        collector.addDescriptorElements(variants.imported, resultLookupElementFactory, withReceiverCast = true)
                        collector.addDescriptorElements(
                            variants.notImportedExtensions,
                            resultLookupElementFactory,
                            withReceiverCast = true,
                            notImported = true
                        )

                        flushToResultSet()
                    }
                }

                if (!receiverTypes.isNullOrEmpty()) {
                    // N.B.: callable references to member extensions are forbidden
                    val shouldCompleteExtensionsFromObjects = when (callTypeAndReceiver.callType) {
                        CallType.DEFAULT, CallType.DOT, CallType.SAFE, CallType.INFIX -> true
                        else -> false
                    }

                    if (shouldCompleteExtensionsFromObjects) {
                        val receiverKotlinTypes = receiverTypes.map { it.type }

                        staticMembersCompletion.completeObjectMemberExtensionsFromIndices(
                            indicesHelper(mayIncludeInaccessible = false),
                            receiverKotlinTypes,
                            callTypeAndReceiver,
                            collector
                        )

                        staticMembersCompletion.completeExplicitAndInheritedMemberExtensionsFromIndices(
                            indicesHelper(mayIncludeInaccessible = false),
                            receiverKotlinTypes,
                            callTypeAndReceiver,
                            collector
                        )
                    }
                }

                if (configuration.staticMembers && prefix.isNotEmpty()) {
                    if (callTypeAndReceiver is CallTypeAndReceiver.DEFAULT) {
                        staticMembersCompletion.completeFromIndices(indicesHelper(false), collector)
                    }
                }
            }
        }

        private fun completeNonImported(lookupElementFactory: LookupElementFactory) {
            if (shouldCompleteTopLevelCallablesFromIndex()) {
                processTopLevelCallables {
                    collector.addDescriptorElements(it, lookupElementFactory, notImported = true)
                    flushToResultSet()
                }
            }

            if (callTypeAndReceiver.receiver == null && prefix.isNotEmpty()) {
                val classKindFilter: ((ClassKind) -> Boolean)? = when (callTypeAndReceiver) {
                    is CallTypeAndReceiver.ANNOTATION -> {
                        { it == ClassKind.ANNOTATION_CLASS }
                    }

                    is CallTypeAndReceiver.DEFAULT, is CallTypeAndReceiver.TYPE -> {
                        { it != ClassKind.ENUM_ENTRY }
                    }

                    else -> null
                }

                if (classKindFilter != null) {
                    val prefixMatcher = if (configuration.useBetterPrefixMatcherForNonImportedClasses)
                        BetterPrefixMatcher(prefixMatcher, collector.bestMatchingDegree)
                    else
                        prefixMatcher

                    addClassesFromIndex(
                        kindFilter = classKindFilter,
                        prefixMatcher = prefixMatcher,
                        completionParameters = parameters,
                        indicesHelper = indicesHelper(true),
                        classifierDescriptorCollector = {
                            collector.addElement(basicLookupElementFactory.createLookupElement(it), notImported = true)
                        },
                        javaClassCollector = {
                            collector.addElement(basicLookupElementFactory.createLookupElementForJavaClass(it), notImported = true)
                        }
                    )
                }
            } else if (callTypeAndReceiver is CallTypeAndReceiver.DOT) {
                val qualifier = bindingContext[BindingContext.QUALIFIER, callTypeAndReceiver.receiver]
                if (qualifier != null) return
                val receiver = callTypeAndReceiver.receiver as? KtSimpleNameExpression ?: return
                val descriptors = mutableListOf<ClassifierDescriptorWithTypeParameters>()
                val fullTextPrefixMatcher = object : PrefixMatcher(receiver.getReferencedName()) {
                    override fun prefixMatches(name: String): Boolean = name == prefix
                    override fun cloneWithPrefix(prefix: String): PrefixMatcher = throw UnsupportedOperationException("Not implemented")
                }

                addClassesFromIndex(
                    kindFilter = { true },
                    prefixMatcher = fullTextPrefixMatcher,
                    completionParameters = parameters.withPosition(receiver, receiver.startOffset),
                    indicesHelper = indicesHelper(false),
                    classifierDescriptorCollector = { descriptors += it },
                    javaClassCollector = { descriptors.addIfNotNull(it.resolveToDescriptor(resolutionFacade)) },
                )

                val foundDescriptors = HashSet<DeclarationDescriptor>()
                val classifiers = descriptors.asSequence().filter {
                    it.kind == ClassKind.OBJECT ||
                            it.kind == ClassKind.ENUM_CLASS ||
                            it.kind == ClassKind.ENUM_ENTRY ||
                            it.hasCompanionObject ||
                            it is JavaClassDescriptor
                }

                for (classifier in classifiers) {
                    val scope = nameExpression?.getResolutionScope(bindingContext) ?: return

                    val desc = classifier.getImportableDescriptor()
                    val newScope = scope.addImportingScope(ExplicitImportsScope(listOf(desc)))

                    val newContext = (nameExpression.parent as KtExpression).analyzeInContext(newScope)

                    val rvHelper = ReferenceVariantsHelper(
                        newContext,
                        resolutionFacade,
                        moduleDescriptor,
                        isVisibleFilter,
                        NotPropertiesService.getNotProperties(position)
                    )

                    val rvCollector = ReferenceVariantsCollector(
                        referenceVariantsHelper = rvHelper,
                        indicesHelper = indicesHelper(true),
                        prefixMatcher = prefixMatcher,
                        nameExpression = nameExpression,
                        callTypeAndReceiver = callTypeAndReceiver,
                        resolutionFacade = resolutionFacade,
                        bindingContext = newContext,
                        importableFqNameClassifier = importableFqNameClassifier,
                        configuration = configuration,
                        allowExpectedDeclarations = allowExpectedDeclarations,
                    )

                    val receiverTypes = detectReceiverTypes(newContext, nameExpression, callTypeAndReceiver)
                    val factory = lookupElementFactory.copy(
                        receiverTypes = receiverTypes,
                        standardLookupElementsPostProcessor = { lookupElement ->
                            val lookupDescriptor = lookupElement.`object`
                                .safeAs<DescriptorBasedDeclarationLookupObject>()
                                ?.descriptor as? MemberDescriptor
                                ?: return@copy lookupElement

                            if (!desc.isAncestorOf(lookupDescriptor, false)) return@copy lookupElement

                            if (lookupDescriptor is CallableMemberDescriptor &&
                                lookupDescriptor.isExtension &&
                                lookupDescriptor.extensionReceiverParameter?.importableFqName != desc.fqNameSafe
                            ) {
                                return@copy lookupElement
                            }

                            val fqNameToImport = lookupDescriptor.containingDeclaration.importableFqName ?: return@copy lookupElement

                            object : LookupElementDecorator<LookupElement>(lookupElement) {
                                val name = fqNameToImport.shortName()
                                val packageName = fqNameToImport.parent()

                                override fun handleInsert(context: InsertionContext) {
                                    super.handleInsert(context)
                                    context.commitDocument()
                                    val file = context.file as? KtFile
                                    if (file != null) {
                                        val receiverInFile = file.findElementAt(receiver.startOffset)
                                            ?.getParentOfType<KtSimpleNameExpression>(false)
                                            ?: return

                                        receiverInFile.mainReference.bindToFqName(fqNameToImport, FORCED_SHORTENING)
                                    }
                                }

                                override fun renderElement(presentation: LookupElementPresentation) {
                                    super.renderElement(presentation)
                                    presentation.appendTailText(
                                        KotlinIdeaCompletionBundle.message(
                                            "presentation.tail.for.0.in.1",
                                            name,
                                            packageName,
                                        ),
                                        true,
                                    )
                                }
                            }
                        },
                    )

                    rvCollector.collectReferenceVariants(descriptorKindFilter) { (imported, notImportedExtensions) ->
                        val unique = imported.asSequence()
                            .filterNot { it.original in foundDescriptors }
                            .onEach { foundDescriptors += it.original }

                        val uniqueNotImportedExtensions = notImportedExtensions.asSequence()
                            .filterNot { it.original in foundDescriptors }
                            .onEach { foundDescriptors += it.original }

                        collector.addDescriptorElements(
                            unique.toList(), factory,
                            prohibitDuplicates = true
                        )

                        collector.addDescriptorElements(
                            uniqueNotImportedExtensions.toList(), factory,
                            notImported = true, prohibitDuplicates = true
                        )

                        flushToResultSet()
                    }
                }
            }
        }

        private fun isStartOfExtensionReceiverFor(): KtCallableDeclaration? {
            val userType = nameExpression!!.parent as? KtUserType ?: return null
            if (userType.qualifier != null) return null
            val typeRef = userType.parent as? KtTypeReference ?: return null
            if (userType != typeRef.typeElement) return null
            return when (val parent = typeRef.parent) {
                is KtNamedFunction -> parent.takeIf { typeRef == it.receiverTypeReference }
                is KtProperty -> parent.takeIf { typeRef == it.receiverTypeReference }
                else -> null
            }
        }
    }

    private fun wasAutopopupRecentlyCancelled(parameters: CompletionParameters) =
        LookupCancelService.getInstance(project).wasAutoPopupRecentlyCancelled(parameters.editor, position.startOffset)

    private val KEYWORDS_ONLY = object : CompletionKind {
        override val descriptorKindFilter: DescriptorKindFilter? get() = null

        private val keywordCompletion = KeywordCompletion(object : KeywordCompletion.LanguageVersionSettingProvider {
            override fun getLanguageVersionSetting(element: PsiElement) = element.languageVersionSettings
            override fun getLanguageVersionSetting(module: Module) = module.languageVersionSettings
        })

        override fun doComplete() {
            val keywordsToSkip = HashSet<String>()
            val keywordValueConsumer = object : KeywordValues.Consumer {
                override fun consume(
                    lookupString: String,
                    expectedInfoMatcher: (ExpectedInfo) -> ExpectedInfoMatch,
                    suitableOnPsiLevel: PsiElement.() -> Boolean,
                    priority: SmartCompletionItemPriority,
                    factory: () -> LookupElement
                ) {
                    keywordsToSkip.add(lookupString)
                    val lookupElement = factory()
                    val matched = expectedInfos.any {
                        val match = expectedInfoMatcher(it)
                        assert(!match.makeNotNullable) { "Nullable keyword values not supported" }
                        match.isMatch()
                    }

                    // 'expectedInfos' is filled with the compiler's insight.
                    // In cases like missing import statement or undeclared variable desired data cannot be retrieved. Here is where we can
                    // analyse PSI and calling 'suitableOnPsiLevel()' does the trick.
                    if (matched || (expectedInfos.isEmpty() && position.suitableOnPsiLevel())) {
                        lookupElement.putUserData(SmartCompletionInBasicWeigher.KEYWORD_VALUE_MATCHED_KEY, Unit)
                        lookupElement.putUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY, priority)
                    }
                    collector.addElement(lookupElement)
                }
            }

            KeywordValues.process(
                keywordValueConsumer,
                callTypeAndReceiver,
                bindingContext,
                resolutionFacade,
                moduleDescriptor,
                isJvmModule
            )

            keywordCompletion.complete(expression ?: position, resultSet.prefixMatcher, isJvmModule) { lookupElement ->
                val keyword = lookupElement.lookupString
                if (keyword in keywordsToSkip) return@complete

                val completionKeywordHandler = DefaultCompletionKeywordHandlerProvider.getHandlerForKeyword(keyword)
                if (completionKeywordHandler != null) {
                    val lookups = completionKeywordHandler.createLookups(parameters, expression, lookupElement, project)
                    collector.addElements(lookups)
                    return@complete
                }

                when (keyword) {
                    // if "this" is parsed correctly in the current context - insert it and all this@xxx items
                    "this" -> {
                        if (expression != null) {
                            collector.addElements(
                                thisExpressionItems(
                                    bindingContext,
                                    expression,
                                    prefix,
                                    resolutionFacade
                                ).map { it.createLookupElement().withInsertHandler {

                                        context, item ->
                                    if(item.lookupString== "this" ||!checkQualifiedThisInStringTemplateCompletion(parameters) )return@withInsertHandler

                                    val document = context.editor.document

                                    val caretOffset = context.editor.caretModel.offset
                                    document.insertString(context.startOffset,"{")

                                    val tailOffset = context.tailOffset
                                    document.insertString(tailOffset, "}")

                                } })
                        } else {
                            // for completion in secondary constructor delegation call
                            collector.addElement(lookupElement)
                        }
                    }

                    // if "return" is parsed correctly in the current context - insert it and all return@xxx items
                    "return" -> {
                        if (expression != null) {
                            collector.addElements(returnExpressionItems(bindingContext, expression))
                        }
                    }

                    "override" -> {
                        collector.addElement(lookupElement)

                        OverridesCompletion(collector, basicLookupElementFactory).complete(position, declaration = null)
                    }

                    "class" -> {
                        if (callTypeAndReceiver !is CallTypeAndReceiver.CALLABLE_REFERENCE) { // otherwise it should be handled by KeywordValues
                            collector.addElement(lookupElement)
                        }
                    }

                    "suspend", "out", "in" -> {
                        if (position.isInsideKtTypeReference) {
                            // aforementioned keyword modifiers are rarely needed in the type references and
                            // most of the time can be quickly prefix-selected by typing the corresponding letter.
                            // We mark them as low-priority, so they do not shadow actual types
                            lookupElement.keywordProbability = KeywordProbability.LOW
                        }

                        collector.addElement(lookupElement)
                    }

                    "break", "continue" -> {
                        if (expression != null) {
                            analyze(expression) {
                                val ktKeywordToken = when (keyword) {
                                    "break" -> KtTokens.BREAK_KEYWORD
                                    "continue" -> KtTokens.CONTINUE_KEYWORD
                                    else -> error("'$keyword' can only be 'break' or 'continue'")
                                }
                                collector.addElements(BreakContinueKeywordHandler(ktKeywordToken).createLookups(this, expression))
                            }
                        }
                    }

                    else -> collector.addElement(lookupElement)
                }
            }
        }
    }

    private val NAMED_ARGUMENTS_ONLY = object : CompletionKind {
        override val descriptorKindFilter: DescriptorKindFilter? get() = null
        override fun doComplete(): Unit = NamedArgumentCompletion.complete(collector, expectedInfos, callTypeAndReceiver.callType)
    }

    private val OPERATOR_NAME = object : CompletionKind {
        override val descriptorKindFilter: DescriptorKindFilter? get() = null

        fun isApplicable(): Boolean {
            if (nameExpression == null || nameExpression != expression) return false
            val func = position.getParentOfType<KtNamedFunction>(strict = false) ?: return false
            val funcNameIdentifier = func.nameIdentifier ?: return false
            val identifierInNameExpression = nameExpression.nextLeaf {
                it is LeafPsiElement && it.elementType == KtTokens.IDENTIFIER
            } ?: return false

            if (!func.hasModifier(KtTokens.OPERATOR_KEYWORD) || identifierInNameExpression != funcNameIdentifier) return false
            val originalFunc = toFromOriginalFileMapper.toOriginalFile(func) ?: return false
            return !originalFunc.isTopLevel || (originalFunc.isExtensionDeclaration())
        }

        override fun doComplete() {
            OperatorNameCompletion.doComplete(collector, descriptorNameFilter)
            flushToResultSet()
        }
    }

    private val DECLARATION_NAME = object : CompletionKind {
        override val descriptorKindFilter: DescriptorKindFilter? get() = null

        override fun doComplete() {
            val declaration = declaration()
            if (declaration is KtParameter && !NameWithTypeCompletion.shouldCompleteParameter(declaration)) {
                return // do not complete also keywords and from unresolved references in such case
            }

            collector.addLookupElementPostProcessor { lookupElement ->
                lookupElement.putUserData(KotlinCompletionCharFilter.SUPPRESS_ITEM_SELECTION_BY_CHARS_ON_TYPING, Unit)
                lookupElement
            }

            KEYWORDS_ONLY.doComplete()

            completeDeclarationNameFromUnresolvedOrOverride(declaration)

            when (declaration) {
                is KtParameter -> completeParameterOrVarNameAndType(withType = true)
                is KtClassOrObject -> {
                    if (declaration.isTopLevel()) {
                        completeTopLevelClassName()
                    }
                }
            }
        }

        override fun shouldDisableAutoPopup(): Boolean = when {
            TemplateManager.getInstance(project).getActiveTemplate(parameters.editor) != null -> true
            declaration() is KtParameter && wasAutopopupRecentlyCancelled(parameters) -> true
            else -> false
        }

        override fun addWeighers(sorter: CompletionSorter): CompletionSorter {
            val declaration = declaration()
            return if (declaration is KtParameter && NameWithTypeCompletion.shouldCompleteParameter(declaration))
                sorter.weighBefore("prefix", VariableOrParameterNameWithTypeCompletion.Weigher)
            else
                sorter
        }

        private fun completeTopLevelClassName() {
            val name = parameters.originalFile.virtualFile.nameWithoutExtension
            if (!(Name.isValidIdentifier(name) && Name.identifier(name).render() == name && name[0].isUpperCase())) return
            if ((parameters.originalFile as KtFile).declarations.any { it is KtClassOrObject && it.name == name }) return

            collector.addElement(LookupElementBuilder.create(name))
        }

        private fun declaration() = position.parent as KtNamedDeclaration
    }

    private val SUPER_QUALIFIER = object : CompletionKind {
        override val descriptorKindFilter: DescriptorKindFilter
            get() = DescriptorKindFilter.NON_SINGLETON_CLASSIFIERS

        override fun doComplete() {
            val classOrObject = position.parents.firstIsInstanceOrNull<KtClassOrObject>() ?: return
            val classDescriptor = resolutionFacade.resolveToDescriptor(classOrObject, BodyResolveMode.PARTIAL) as ClassDescriptor
            var superClasses = classDescriptor.defaultType.constructor.supertypesWithAny()
                .mapNotNull { it.constructor.declarationDescriptor as? ClassDescriptor }

            if (callTypeAndReceiver.receiver != null) {
                val referenceVariantsSet = referenceVariantsCollector!!.collectReferenceVariants(descriptorKindFilter).imported.toSet()
                superClasses = superClasses.filter { it in referenceVariantsSet }
            }

            superClasses
                .map { basicLookupElementFactory.createLookupElement(it, qualifyNestedClasses = true, includeClassTypeArguments = false) }
                .forEach { collector.addElement(it) }
        }
    }

    private fun completeDeclarationNameFromUnresolvedOrOverride(declaration: KtNamedDeclaration) {
        if (declaration is KtCallableDeclaration && declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            OverridesCompletion(collector, basicLookupElementFactory).complete(position, declaration)
        } else {
            val referenceScope = referenceScope(declaration) ?: return
            val originalScope = toFromOriginalFileMapper.toOriginalFile(referenceScope) ?: return
            val afterOffset = if (referenceScope is KtBlockExpression) parameters.offset else null
            val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration]
            FromUnresolvedNamesCompletion(collector, prefixMatcher).addNameSuggestions(originalScope, afterOffset, descriptor)
        }
    }

    private fun addClassesFromIndex(
        kindFilter: (ClassKind) -> Boolean,
        prefixMatcher: PrefixMatcher,
        completionParameters: CompletionParameters,
        indicesHelper: KotlinIndicesHelper,
        classifierDescriptorCollector: (ClassifierDescriptorWithTypeParameters) -> Unit,
        javaClassCollector: (PsiClass) -> Unit,
    ) {
        AllClassesCompletion(
            parameters = completionParameters,
            kotlinIndicesHelper = indicesHelper,
            prefixMatcher = prefixMatcher,
            resolutionFacade = resolutionFacade,
            kindFilter = kindFilter,
            includeTypeAliases = true,
            includeJavaClassesNotToBeUsed = configuration.javaClassesNotToBeUsed,
        ).collect({ processWithShadowedFilter(it, classifierDescriptorCollector) }, javaClassCollector)
    }

    private fun addReferenceVariantElements(lookupElementFactory: LookupElementFactory, descriptorKindFilter: DescriptorKindFilter) {
        fun addReferenceVariants(referenceVariants: ReferenceVariants) {
            collector.addDescriptorElements(
                referenceVariantsHelper.excludeNonInitializedVariable(referenceVariants.imported, position),
                lookupElementFactory, prohibitDuplicates = true
            )

            collector.addDescriptorElements(
                referenceVariants.notImportedExtensions, lookupElementFactory,
                notImported = true, prohibitDuplicates = true
            )
        }

        val referenceVariantsCollector = referenceVariantsCollector!!
        referenceVariantsCollector.collectReferenceVariants(descriptorKindFilter) { referenceVariants ->
            addReferenceVariants(referenceVariants)
            flushToResultSet()
        }
    }

    private fun completeParameterOrVarNameAndType(withType: Boolean) {
        val nameWithTypeCompletion = VariableOrParameterNameWithTypeCompletion(
            collector,
            basicLookupElementFactory,
            prefixMatcher,
            resolutionFacade,
            withType,
        )

        collector.restartCompletionOnPrefixChange(NameWithTypeCompletion.prefixEndsWithUppercaseLetterPattern)

        nameWithTypeCompletion.addFromParametersInFile(position, resolutionFacade, isVisibleFilterCheckAlways)
        flushToResultSet()

        nameWithTypeCompletion.addFromImportedClasses(position, bindingContext, isVisibleFilterCheckAlways)
        flushToResultSet()

        nameWithTypeCompletion.addFromAllClasses(parameters, indicesHelper(false))
    }
}

private val USUALLY_START_UPPER_CASE = DescriptorKindFilter(
    DescriptorKindFilter.CLASSIFIERS_MASK or DescriptorKindFilter.FUNCTIONS_MASK,
    listOf(NonSamConstructorFunctionExclude, DescriptorKindExclude.Extensions /* needed for faster getReferenceVariants */)
)

private val USUALLY_START_LOWER_CASE = DescriptorKindFilter(
    DescriptorKindFilter.CALLABLES_MASK or DescriptorKindFilter.PACKAGES_MASK,
    listOf(SamConstructorDescriptorKindExclude)
)

private object NonSamConstructorFunctionExclude : DescriptorKindExclude() {
    override fun excludes(descriptor: DeclarationDescriptor) = descriptor is FunctionDescriptor && descriptor !is SamConstructorDescriptor
    override val fullyExcludedDescriptorKinds: Int get() = 0
}
