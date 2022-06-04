// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.impl.CompositeShortNamesCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.caches.IDEKotlinBinaryClassCache
import org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.forceEnableSamAdapters
import org.jetbrains.kotlin.idea.core.extension.KotlinIndicesHelperExtension
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.search.excludeKotlinSources
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.idea.util.application.withPsiAttachment
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.sam.SamAdapterDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.deprecation.DeprecationResolver
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.scopes.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.types.isError
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class KotlinIndicesHelper(
    private val resolutionFacade: ResolutionFacade,
    private val scope: GlobalSearchScope,
    visibilityFilter: (DeclarationDescriptor) -> Boolean,
    private val declarationTranslator: (KtDeclaration) -> KtDeclaration? = { it },
    applyExcludeSettings: Boolean = true,
    private val filterOutPrivate: Boolean = true,
    private val file: KtFile? = null
) {

    private val moduleDescriptor = resolutionFacade.moduleDescriptor
    private val project = resolutionFacade.project
    private val scopeWithoutKotlin = scope.excludeKotlinSources(project) as GlobalSearchScope

    @OptIn(FrontendInternals::class)
    private val descriptorFilter: (DeclarationDescriptor) -> Boolean = filter@{
        if (resolutionFacade.frontendService<DeprecationResolver>().isHiddenInResolution(it)) return@filter false
        if (!visibilityFilter(it)) return@filter false
        if (applyExcludeSettings && it.isExcludedFromAutoImport(project, file)) return@filter false
        true
    }

    fun getTopLevelCallablesByName(name: String): Collection<CallableDescriptor> {
        val declarations = LinkedHashSet<KtNamedDeclaration>()
        declarations.addTopLevelNonExtensionCallablesByName(KotlinFunctionShortNameIndex.getInstance(), name)
        declarations.addTopLevelNonExtensionCallablesByName(KotlinPropertyShortNameIndex.getInstance(), name)
        return declarations
            .flatMap { it.resolveToDescriptors<CallableDescriptor>() }
            .filter { descriptorFilter(it) }
    }

    private fun MutableSet<KtNamedDeclaration>.addTopLevelNonExtensionCallablesByName(
        index: StringStubIndexExtension<out KtNamedDeclaration>,
        name: String
    ) {
        index.get(name, project, scope)
            .filterTo(this) { it.parent is KtFile && it is KtCallableDeclaration && it.receiverTypeReference == null }
    }

    fun getTopLevelExtensionOperatorsByName(name: String): Collection<FunctionDescriptor> {
        return KotlinFunctionShortNameIndex.getInstance().get(name, project, scope)
            .filter { it.parent is KtFile && it.receiverTypeReference != null && it.hasModifier(KtTokens.OPERATOR_KEYWORD) }
            .flatMap { it.resolveToDescriptors<FunctionDescriptor>() }
            .filter { descriptorFilter(it) && it.extensionReceiverParameter != null }
            .distinct()
    }

    fun getMemberOperatorsByName(name: String): Collection<FunctionDescriptor> {
        return KotlinFunctionShortNameIndex.getInstance().get(name, project, scope)
            .filter { it.parent is KtClassBody && it.receiverTypeReference == null && it.hasModifier(KtTokens.OPERATOR_KEYWORD) }
            .flatMap { it.resolveToDescriptors<FunctionDescriptor>() }
            .filter { descriptorFilter(it) && it.extensionReceiverParameter == null }
            .distinct()
    }

    fun processTopLevelCallables(nameFilter: (String) -> Boolean, processor: (CallableDescriptor) -> Unit) {
        val callableDeclarationProcessor = Processor<KtCallableDeclaration> { declaration ->
            if (declaration.receiverTypeReference != null) return@Processor true
            if (filterOutPrivate && declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) return@Processor true
            ProgressManager.checkCanceled()
            declaration.resolveToDescriptors<CallableDescriptor>().forEach { descriptor ->
                if (descriptorFilter(descriptor)) {
                    processor(descriptor)
                }
            }
            true
        }

        val filter: (String) -> Boolean = { key -> nameFilter(key.substringAfterLast('.', key)) }
        KotlinTopLevelFunctionFqnNameIndex.getInstance().processAllElements(project, scope, filter, callableDeclarationProcessor)

        KotlinTopLevelPropertyFqnNameIndex.getInstance().processAllElements(project, scope, filter, callableDeclarationProcessor)
    }

    fun getCallableTopLevelExtensions(
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        position: KtExpression,
        bindingContext: BindingContext,
        receiverTypeFromDiagnostic: KotlinType?,
        nameFilter: (String) -> Boolean
    ): Collection<CallableDescriptor> {
        val receiverTypes = callTypeAndReceiver.receiverTypes(
            bindingContext, position, moduleDescriptor, resolutionFacade, stableSmartCastsOnly = false
        )

        return if (receiverTypes == null || receiverTypes.all { it.isError }) {
            if (receiverTypeFromDiagnostic != null)
                getCallableTopLevelExtensions(callTypeAndReceiver, listOf(receiverTypeFromDiagnostic), nameFilter)
            else
                emptyList()
        } else {
            getCallableTopLevelExtensions(callTypeAndReceiver, receiverTypes, nameFilter)
        }
    }

    fun getCallableTopLevelExtensions(
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean,
        declarationFilter: (KtDeclaration) -> Boolean = { true }
    ): Collection<CallableDescriptor> {
        if (receiverTypes.isEmpty()) return emptyList()

        val suitableTopLevelExtensions = mutableListOf<CallableDescriptor>()
        KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE.processSuitableExtensions(
            receiverTypes,
            nameFilter,
            declarationFilter,
            callTypeAndReceiver,
            processor = suitableTopLevelExtensions::add
        )

        val additionalDescriptors = ArrayList<CallableDescriptor>()

        val lookupLocation = this.file?.let { KotlinLookupLocation(it) } ?: NoLookupLocation.FROM_IDE
        for (extension in @Suppress("DEPRECATION") KotlinIndicesHelperExtension.getInstances(project)) {
            extension.appendExtensionCallables(additionalDescriptors, moduleDescriptor, receiverTypes, nameFilter, lookupLocation)
        }

        return if (additionalDescriptors.isNotEmpty()) {
            suitableTopLevelExtensions + additionalDescriptors
        } else {
            suitableTopLevelExtensions
        }
    }

    fun processCallableExtensionsDeclaredInObjects(
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean,
        declarationFilter: (KtDeclaration) -> Boolean = { true },
        processor: (CallableDescriptor) -> Unit
    ) {
        if (receiverTypes.isEmpty()) return

        KotlinExtensionsInObjectsByReceiverTypeIndex.INSTANCE.processSuitableExtensions(
            receiverTypes,
            nameFilter,
            declarationFilter,
            callTypeAndReceiver,
            processor
        )
    }

    fun resolveTypeAliasesUsingIndex(type: KotlinType, originalTypeName: String): Set<TypeAliasDescriptor> {
        val typeConstructor = type.constructor

        val index = KotlinTypeAliasByExpansionShortNameIndex.INSTANCE
        val out = LinkedHashMap<FqName, TypeAliasDescriptor>()

        fun searchRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            index[typeName, project, scope].asSequence()
                .filter { it in scope }
                .flatMap { it.resolveToDescriptors<TypeAliasDescriptor>().asSequence() }
                .filter { it.expandedType.constructor == typeConstructor }
                .filter { out.putIfAbsent(it.fqNameSafe, it) == null }
                .map { it.name.asString() }
                .forEach(::searchRecursively)
        }

        searchRecursively(originalTypeName)
        return out.values.toSet()
    }

    private fun KotlinExtensionsByReceiverTypeIndex.processSuitableExtensions(
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean,
        declarationFilter: (KtDeclaration) -> Boolean,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        processor: (CallableDescriptor) -> Unit
    ) {
        val receiverTypeNames = collectAllNamesOfTypes(receiverTypes)

        val callType = callTypeAndReceiver.callType
        val processed = HashSet<CallableDescriptor>()

        val declarationProcessor = Processor<KtCallableDeclaration> { callableDeclaration ->
            // Check that function or property with the given qualified name can be resolved in given scope and called on given receiver
            ProgressManager.checkCanceled()
            if (declarationFilter(callableDeclaration)) {
                callableDeclaration.resolveToDescriptors<CallableDescriptor>().forEach { descriptor ->
                    if (descriptor.extensionReceiverParameter != null && descriptorFilter(descriptor)) {
                        for (callableDescriptor in descriptor.substituteExtensionIfCallable(receiverTypes, callType)){
                            if (processed.add(callableDescriptor)) processor(callableDescriptor)
                        }
                    }
                }
            }
            true
        }
        processAllElements(
            project,
            scope,
            { receiverTypeNameFromKey(it) in receiverTypeNames && nameFilter(callableNameFromKey(it)) },
            declarationProcessor
        )
    }

    private fun possibleTypeAliasExpansionNames(originalTypeName: String): Set<String> {
        val index = KotlinTypeAliasByExpansionShortNameIndex.INSTANCE
        val out = mutableSetOf<String>()

        fun searchRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            index[typeName, project, scope].asSequence()
                .filter { it in scope }
                .mapNotNull(KtTypeAlias::getName)
                .filter(out::add)
                .forEach(::searchRecursively)
        }

        searchRecursively(originalTypeName)
        return out
    }

    private fun MutableCollection<String>.addTypeNames(type: KotlinType) {
        val constructor = type.constructor
        constructor.declarationDescriptor?.name?.asString()?.let { typeName ->
            add(typeName)
            addAll(possibleTypeAliasExpansionNames(typeName))
        }
        constructor.supertypes.forEach { addTypeNames(it) }
    }

    private fun collectAllNamesOfTypes(types: Collection<KotlinType>): HashSet<String> {
        val receiverTypeNames = HashSet<String>()
        types.forEach { receiverTypeNames.addTypeNames(it) }
        return receiverTypeNames
    }

    fun getJvmClassesByName(name: String): Collection<ClassDescriptor> {
        return PsiShortNamesCache.getInstance(project).getClassesByName(name, scope)
            .filter { it in scope && it.containingFile != null }
            .mapNotNull { it.resolveToDescriptor(resolutionFacade) }
            .filter(descriptorFilter)
            .toSet()
    }

    fun getKotlinEnumsByName(name: String): Collection<DeclarationDescriptor> {
        return KotlinClassShortNameIndex.getInstance()[name, project, scope]
            .filter { it is KtEnumEntry && it in scope }
            .flatMap { it.resolveToDescriptors<DeclarationDescriptor>() }
            .filter(descriptorFilter)
            .toSet()
    }

    fun processJvmCallablesByName(
        name: String,
        filter: (PsiMember) -> Boolean,
        processor: (CallableDescriptor) -> Unit
    ) {
        val javaDeclarations = getJavaCallables(name, PsiShortNamesCache.getInstance(project))
        val processed = HashSet<CallableDescriptor>()
        for (javaDeclaration in javaDeclarations) {
            ProgressManager.checkCanceled()
            if (javaDeclaration is KtLightElement<*, *>) continue
            if (!filter(javaDeclaration as PsiMember)) continue
            val descriptor = javaDeclaration.getJavaMemberDescriptor(resolutionFacade) as? CallableDescriptor ?: continue
            if (!processed.add(descriptor)) continue
            if (!descriptorFilter(descriptor)) continue
            processor(descriptor)
        }
    }

    /*
     * This is a dirty work-around to filter out results from BrShortNamesCache.
     * BrShortNamesCache creates a synthetic class (LightBrClass), which traverses all annotated properties
     *     in a module inside "myFieldCache" (and in Kotlin light classes too, of course).
     * It triggers the light classes compilation in the UI thread inside our static field import quick-fix.
     */
    private val filteredShortNamesCaches: List<PsiShortNamesCache>? by lazy {
        val shortNamesCache = PsiShortNamesCache.getInstance(project)
        if (shortNamesCache is CompositeShortNamesCache) {
            try {
                fun getMyCachesField(clazz: Class<out PsiShortNamesCache>): Field = try {
                    clazz.getDeclaredField("myCaches")
                } catch (e: NoSuchFieldException) {
                    // In case the class is proguarded
                    clazz.declaredFields.first {
                        Modifier.isPrivate(it.modifiers) && Modifier.isFinal(it.modifiers) && !Modifier.isStatic(it.modifiers)
                                && it.type.isArray && it.type.componentType == PsiShortNamesCache::class.java
                    }
                }

                val myCachesField = getMyCachesField(shortNamesCache::class.java)
                val previousIsAccessible = myCachesField.isAccessible
                try {
                    myCachesField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    return@lazy (myCachesField.get(shortNamesCache) as Array<PsiShortNamesCache>).filter {
                        it !is KotlinShortNamesCache
                                && it::class.java.name != "com.android.tools.idea.databinding.BrShortNamesCache"
                                && it::class.java.name != "com.android.tools.idea.databinding.DataBindingComponentShortNamesCache"
                                && it::class.java.name != "com.android.tools.idea.databinding.DataBindingShortNamesCache"
                    }
                } finally {
                    myCachesField.isAccessible = previousIsAccessible
                }
            } catch (thr: Throwable) {
                // Our dirty hack isn't working
            }
        }

        return@lazy null
    }

    private fun getJavaCallables(name: String, shortNamesCache: PsiShortNamesCache): Sequence<Any> {
        filteredShortNamesCaches?.let { caches -> return getCallablesByName(name, scopeWithoutKotlin, caches) }
        return shortNamesCache.getFieldsByNameUnfiltered(name, scopeWithoutKotlin) +
                shortNamesCache.getMethodsByNameUnfiltered(name, scopeWithoutKotlin)
    }

    private fun getCallablesByName(name: String, scope: GlobalSearchScope, caches: List<PsiShortNamesCache>): Sequence<Any> {
        return caches.asSequence().flatMap { cache ->
            cache.getMethodsByNameUnfiltered(name, scope) + cache.getFieldsByNameUnfiltered(name, scope)
        }
    }

    // getMethodsByName() removes duplicates from returned set of names, which can be excessively slow
    // if the number of candidates is large (KT-16071) and is unnecessary because Kotlin performs its own
    // duplicate filtering later
    private fun PsiShortNamesCache.getMethodsByNameUnfiltered(name: String, scope: GlobalSearchScope): Sequence<PsiMethod> {
        val result = arrayListOf<PsiMethod>()
        processMethodsWithName(name, scope) { result.add(it) }
        return result.asSequence()
    }

    private fun PsiShortNamesCache.getFieldsByNameUnfiltered(name: String, scope: GlobalSearchScope): Sequence<PsiField> {
        val result = arrayListOf<PsiField>()
        processFieldsWithName(name, { field -> result.add(field); true }, scope, null)
        return result.asSequence()
    }

    /**
     * Collect all callable object members (including inherited members) with the specified name.
     *
     * @param callTypeAndReceiver the call type and receiver at the call site
     * @param receiverTypes the list of the types allowed as receivers
     * @param nameFilter the function to filter candidates with relevant names (e.g., a prefix matcher for the completion)
     *
     * Unit tests are part of the autoimport quickfix:
     * [org.jetbrains.kotlin.idea.quickfix.QuickFixTestGenerated.AutoImports.CallablesDeclaredInClasses]
     */
    fun processAllCallablesFromSubclassObjects(
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        receiverTypes: Collection<KotlinType>,
        nameFilter: (String) -> Boolean,
        processor: (CallableDescriptor) -> Unit
    ) {
        val descriptorKindFilter = DescriptorKindFilter.CALLABLES

        val objectDeclarationProcessor = Processor<KtObjectDeclaration> { objectDeclaration ->
            objectDeclaration.resolveToDescriptors<ClassDescriptor>().asSequence()
                .map { it.unsubstitutedMemberScope }
                .flatMap { it.getDescriptorsFiltered(descriptorKindFilter) { name -> !name.isSpecial && nameFilter(name.identifier) } }
                .filterIsInstance<CallableMemberDescriptor>()
                .forEach { descriptor ->
                    ProgressManager.checkCanceled()
                    if (descriptor.isExtension) {
                        descriptor.substituteExtensionIfCallable(receiverTypes, callTypeAndReceiver.callType).forEach(processor)
                    } else {
                        processor(descriptor)
                    }
                }
            true
        }

        KotlinSubclassObjectNameIndex.getInstance().processAllElements(project, scope, processor = objectDeclarationProcessor)
    }

    /**
     * Get descriptors for each candidate method/property with a specified name and run a processor on each of them.
     *
     * @param name the callable name
     * @param callTypeAndReceiver expected call type and receiver
     * @param position a Kotlin PSI element where the autoimport quickfix or completion is performed
     * @param bindingContext a binding context of the element
     * @param processor the processor function to run for each descriptor
     */
    fun processAllCallablesInSubclassObjects(
        name: String,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        position: KtExpression,
        bindingContext: BindingContext,
        processor: (CallableDescriptor) -> Unit
    ) {

        val receiverTypes = callTypeAndReceiver.receiverTypes(
            bindingContext, position, moduleDescriptor, resolutionFacade,
            stableSmartCastsOnly = false
        ) ?: return

        processAllCallablesFromSubclassObjects(
            callTypeAndReceiver,
            receiverTypes,
            nameFilter = { it == name },
            processor
        )
    }

    fun processKotlinCallablesByName(
        name: String,
        filter: (KtNamedDeclaration) -> Boolean,
        processor: (CallableDescriptor) -> Unit
    ) {
        val functions: Sequence<KtCallableDeclaration> = KotlinFunctionShortNameIndex.getInstance().get(name, project, scope).asSequence()
        val properties: Sequence<KtNamedDeclaration> = KotlinPropertyShortNameIndex.getInstance().get(name, project, scope).asSequence()
        val processed = HashSet<CallableDescriptor>()
        for (declaration in functions + properties) {
            ProgressManager.checkCanceled()
            if (!filter(declaration)) continue

            for (descriptor in declaration.resolveToDescriptors<CallableDescriptor>()) {
                if (!processed.add(descriptor)) continue
                if (!descriptorFilter(descriptor)) continue
                processor(descriptor)
            }
        }
    }

    fun processKotlinClasses(
        nameFilter: (String) -> Boolean,
        psiFilter: (KtDeclaration) -> Boolean = { true },
        kindFilter: (ClassKind) -> Boolean = { true },
        processor: (ClassDescriptor) -> Unit
    ) {
        val classOrObjectProcessor = Processor<KtClassOrObject> { classOrObject ->
            classOrObject.resolveToDescriptorsWithHack(psiFilter).forEach {
                val descriptor = it as? ClassDescriptor ?: return@forEach
                ProgressManager.checkCanceled()
                if (kindFilter(descriptor.kind) && descriptorFilter(descriptor)) {
                    processor(descriptor)
                }
            }
            true
        }
        KotlinFullClassNameIndex.getInstance()
            .processAllElements(project, scope, { nameFilter(it.substringAfterLast('.')) }, classOrObjectProcessor)
    }

    fun processTopLevelTypeAliases(nameFilter: (String) -> Boolean, processor: (TypeAliasDescriptor) -> Unit) {
        val typeAliasProcessor = Processor<KtTypeAlias> { typeAlias ->
            typeAlias.resolveToDescriptors<TypeAliasDescriptor>().forEach {
                ProgressManager.checkCanceled()
                if (descriptorFilter(it)) {
                    processor(it)
                }
            }
            true
        }
        KotlinTopLevelTypeAliasFqNameIndex.getInstance()
            .processAllElements(project, scope, { nameFilter(it.substringAfterLast('.')) }, typeAliasProcessor)
    }

    fun processObjectMembers(
        descriptorKindFilter: DescriptorKindFilter,
        nameFilter: (String) -> Boolean,
        filter: (KtNamedDeclaration, KtObjectDeclaration) -> Boolean,
        processor: (DeclarationDescriptor) -> Unit
    ) {
        val namedDeclarationProcessor = Processor<KtNamedDeclaration> { declaration ->
            val objectDeclaration = declaration.parent.parent as? KtObjectDeclaration ?: return@Processor true
            if (objectDeclaration.isObjectLiteral()) return@Processor true
            if (filterOutPrivate && declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) return@Processor true
            if (!filter(declaration, objectDeclaration)) return@Processor true
            ProgressManager.checkCanceled()
            declaration.resolveToDescriptors<CallableDescriptor>().forEach { descriptor ->
                if (descriptorKindFilter.accepts(descriptor) && descriptorFilter(descriptor)) {
                    processor(descriptor)
                }
            }
            true
        }

        if (descriptorKindFilter.kindMask.and(DescriptorKindFilter.FUNCTIONS_MASK) != 0) {
            KotlinFunctionShortNameIndex.getInstance().processAllElements(project, scope, nameFilter, namedDeclarationProcessor)
        }
        if (descriptorKindFilter.kindMask.and(DescriptorKindFilter.VARIABLES_MASK) != 0) {
            KotlinPropertyShortNameIndex.getInstance().processAllElements(project, scope, nameFilter, namedDeclarationProcessor)
        }
    }

    fun processJavaStaticMembers(
        descriptorKindFilter: DescriptorKindFilter,
        nameFilter: (String) -> Boolean,
        processor: (DeclarationDescriptor) -> Unit
    ) {
        val shortNamesCache = PsiShortNamesCache.getInstance(project)

        val allMethodNames = hashSetOf<String>()
        shortNamesCache.processAllMethodNames(
            { name -> if (nameFilter(name)) allMethodNames.add(name); true },
            scopeWithoutKotlin,
            null
        )
        for (name in allMethodNames) {
            ProgressManager.checkCanceled()

            for (method in shortNamesCache.getMethodsByName(name, scopeWithoutKotlin).filterNot { it is KtLightElement<*, *> }) {
                if (!method.hasModifierProperty(PsiModifier.STATIC)) continue
                if (filterOutPrivate && method.hasModifierProperty(PsiModifier.PRIVATE)) continue
                if (method.containingClass?.parent !is PsiFile) continue // only top-level classes
                val descriptor = method.getJavaMemberDescriptor(resolutionFacade) ?: continue
                val container = descriptor.containingDeclaration as? ClassDescriptor ?: continue
                if (descriptorKindFilter.accepts(descriptor) && descriptorFilter(descriptor)) {
                    processor(descriptor)

                    // SAM-adapter
                    @OptIn(FrontendInternals::class)
                    val syntheticScopes = resolutionFacade.getFrontendService(SyntheticScopes::class.java).forceEnableSamAdapters()
                    val contributedFunctions = container.staticScope.getContributedFunctions(descriptor.name, NoLookupLocation.FROM_IDE)

                    syntheticScopes.collectSyntheticStaticFunctions(contributedFunctions, NoLookupLocation.FROM_IDE)
                        .filterIsInstance<SamAdapterDescriptor<*>>()
                        .firstOrNull { it.baseDescriptorForSynthetic.original == descriptor.original }
                        ?.let { processor(it) }
                }
            }
        }

        val allFieldNames = hashSetOf<String>()
        shortNamesCache.processAllFieldNames({ name -> if (nameFilter(name)) allFieldNames.add(name); true }, scopeWithoutKotlin, null)
        for (name in allFieldNames) {
            ProgressManager.checkCanceled()

            for (field in shortNamesCache.getFieldsByName(name, scopeWithoutKotlin).filterNot { it is KtLightElement<*, *> }) {
                if (!field.hasModifierProperty(PsiModifier.STATIC)) continue
                if (filterOutPrivate && field.hasModifierProperty(PsiModifier.PRIVATE)) continue
                val descriptor = field.getJavaMemberDescriptor(resolutionFacade) ?: continue
                if (descriptorKindFilter.accepts(descriptor) && descriptorFilter(descriptor)) {
                    processor(descriptor)
                }
            }
        }
    }

    private inline fun <reified TDescriptor : Any> KtNamedDeclaration.resolveToDescriptors(): Collection<TDescriptor> {
        return resolveToDescriptorsWithHack { true }.filterIsInstance<TDescriptor>()
    }

    private fun KtNamedDeclaration.resolveToDescriptorsWithHack(
        psiFilter: (KtDeclaration) -> Boolean
    ): Collection<DeclarationDescriptor> {
        val ktFile = containingFile
        if (ktFile !is KtFile) {
            // https://ea.jetbrains.com/browser/ea_problems/219256
            LOG.error(
                KotlinExceptionWithAttachments("KtElement not inside KtFile ($ktFile, is valid: ${ktFile.isValid})")
                    .withAttachment("file", ktFile)
                    .withAttachment("virtualFile", containingFile.virtualFile)
                    .withAttachment("compiledFile", IDEKotlinBinaryClassCache.getInstance().isKotlinJvmCompiledFile(containingFile.virtualFile))
                    .withAttachment("element", this)
                    .withAttachment("type", javaClass)
                    .withPsiAttachment("file.kt", ktFile)
            )

            return emptyList()
        }

        if (ktFile.isCompiled) { //TODO: it's temporary while resolveToDescriptor does not work for compiled declarations
            val fqName = fqName ?: return emptyList()
            return resolutionFacade.resolveImportReference(moduleDescriptor, fqName)
        } else {
            val translatedDeclaration = declarationTranslator(this) ?: return emptyList()
            if (!psiFilter(translatedDeclaration)) return emptyList()

            return listOfNotNull(resolutionFacade.resolveToDescriptor(translatedDeclaration))
        }
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinIndicesHelper::class.java)
    }
}

