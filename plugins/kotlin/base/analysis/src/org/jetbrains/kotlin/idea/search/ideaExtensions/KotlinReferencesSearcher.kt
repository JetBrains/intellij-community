// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.descendantsOfType
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.asJava.LightClassUtil.getLightClassMethods
import org.jetbrains.kotlin.asJava.LightClassUtil.getLightClassPropertyMethods
import org.jetbrains.kotlin.asJava.LightClassUtil.getLightFieldForCompanionObject
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.base.util.excludeFileTypes
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ExpectActualSupport
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.getElementToSearch
import org.jetbrains.kotlin.idea.search.KOTLIN_NAMED_ARGUMENT_SEARCH_CONTEXT
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.dataClassComponentMethodName
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.filterDataClassComponentsIfDisabled
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.getClassNameForCompanionObject
import org.jetbrains.kotlin.idea.search.effectiveSearchScope
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions.Companion.Empty
import org.jetbrains.kotlin.idea.search.isOnlyKotlinSearch
import org.jetbrains.kotlin.idea.search.usagesSearch.operators.DestructuringDeclarationReferenceSearcher
import org.jetbrains.kotlin.idea.search.usagesSearch.operators.OperatorReferenceSearcher
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

data class KotlinReferencesSearchOptions(
    val acceptCallableOverrides: Boolean = false,
    val acceptOverloads: Boolean = false,
    val acceptExtensionsOfDeclarationClass: Boolean = false,
    val acceptCompanionObjectMembers: Boolean = false,
    val acceptImportAlias: Boolean = true,
    val searchForComponentConventions: Boolean = true,
    val searchForOperatorConventions: Boolean = true,
    val searchNamedArguments: Boolean = true,
    val searchForExpectedUsages: Boolean = true
) {
    fun anyEnabled(): Boolean = acceptCallableOverrides || acceptOverloads || acceptExtensionsOfDeclarationClass

    companion object {
        val Empty = KotlinReferencesSearchOptions()

    }
}

interface KotlinAwareReferencesSearchParameters {
    val kotlinOptions: KotlinReferencesSearchOptions
}

class KotlinReferencesSearchParameters(
    elementToSearch: PsiElement,
    scope: SearchScope = runReadAction { elementToSearch.project.allScope() },
    ignoreAccessScope: Boolean = false,
    optimizer: SearchRequestCollector? = null,
    override val kotlinOptions: KotlinReferencesSearchOptions = Empty
) : ReferencesSearch.SearchParameters(elementToSearch, scope, ignoreAccessScope, optimizer), KotlinAwareReferencesSearchParameters

class KotlinMethodReferencesSearchParameters(
    elementToSearch: PsiMethod,
    scope: SearchScope = runReadAction { elementToSearch.project.allScope() },
    strictSignatureSearch: Boolean = true,
    override val kotlinOptions: KotlinReferencesSearchOptions = Empty
) : MethodReferencesSearch.SearchParameters(elementToSearch, scope, strictSignatureSearch), KotlinAwareReferencesSearchParameters

class KotlinAliasedImportedElementSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference?>) {
        val kotlinOptions = (parameters as? KotlinAwareReferencesSearchParameters)?.kotlinOptions ?: Empty
        if (!kotlinOptions.acceptImportAlias) return

        val queryFunction = DumbService.getInstance(parameters.project).runReadActionInSmartMode(Computable {
            val element = parameters.elementToSearch

            if (!element.isValid) return@Computable null
            val unwrappedElement = element.namedUnwrappedElement ?: return@Computable null
            val elementToSearch = getElementToSearch(kotlinOptions, unwrappedElement)

            val name = elementToSearch.name
            if (name.isNullOrBlank()) return@Computable null

            val effectiveSearchScope = parameters.effectiveSearchScope(elementToSearch)

            val collector = parameters.optimizer
            val session = collector.searchSession
            val function = {
                collector.searchWord(
                    name,
                    effectiveSearchScope,
                    UsageSearchContext.IN_CODE,
                    true,
                    elementToSearch,
                    AliasProcessor(elementToSearch, session)
                )
            }
            function
        })
        queryFunction?.invoke()
    }

    private class AliasProcessor(
        private val myTarget: PsiElement,
        private val mySession: SearchSession
    ) : RequestResultProcessor(myTarget) {
        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            val importStatement = element.parent as? KtImportDirective ?: return true
            val alias = importStatement.alias ?: return true
            val importAlias = alias.name ?: return true

            val reference = importStatement.importedReference?.getQualifiedElementSelector()?.mainReference ?: return true
            if (!reference.isReferenceTo(myTarget)) {
                return true
            }

            val collector = SearchRequestCollector(mySession)
            val fileScope: SearchScope = LocalSearchScope(element.containingFile)
            collector.searchWord(importAlias, fileScope, UsageSearchContext.IN_CODE, true, alias)
            return PsiSearchHelper.getInstance(element.project).processRequests(collector, consumer)
        }
    }
}

class KotlinReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {

    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val processor = QueryProcessor(queryParameters, consumer)
        processor.process()
        processor.executeLongRunningTasks()
    }

    private class QueryProcessor(val queryParameters: ReferencesSearch.SearchParameters, val consumer: Processor<in PsiReference>) {

        private val kotlinOptions = queryParameters.safeAs<KotlinAwareReferencesSearchParameters>()?.kotlinOptions ?: Empty

        private val longTasks = ContainerUtil.createConcurrentList<() -> Unit>()

        fun executeLongRunningTasks() {
            while (longTasks.isNotEmpty()) {
                longTasks.popLast()()
            }
        }

        fun process() {
            var element: SmartPsiElementPointer<PsiElement>? = null
            var classNameForCompanionObject: String? = null

            val (elementToSearchPointer: SmartPsiElementPointer<PsiNamedElement>, effectiveSearchScope) =
                DumbService.getInstance(queryParameters.project).runReadActionInSmartMode( Computable{
                val psiElement = queryParameters.elementToSearch

                if (!psiElement.isValid) return@Computable null

                val unwrappedElement = psiElement.namedUnwrappedElement ?: return@Computable null

                val elementToSearch = getElementToSearch(kotlinOptions, unwrappedElement)

                val effectiveSearchScope = queryParameters.effectiveSearchScope(elementToSearch)

                element = SmartPointerManager.createPointer(psiElement)
                classNameForCompanionObject = elementToSearch.getClassNameForCompanionObject()

                SmartPointerManager.createPointer(elementToSearch) to effectiveSearchScope
            }) ?: return

            runReadAction { element?.element } ?: return

            runReadAction {
                elementToSearchPointer.element?.let { elementToSearch ->
                    val refFilter: (PsiReference) -> Boolean = when (elementToSearch) {
                        is KtParameter -> ({ ref: PsiReference -> !ref.isNamedArgumentReference()/* they are processed later*/ })
                        else -> ({ true })
                    }

                    val resultProcessor = KotlinRequestResultProcessor(elementToSearch, filter = refFilter, options = kotlinOptions)
                    if (kotlinOptions.anyEnabled() || elementToSearch is KtNamedDeclaration && elementToSearch.isExpectDeclaration()) {
                        elementToSearch.name?.let { name ->
                            longTasks.add {
                                // Check difference with default scope
                                runReadAction { elementToSearchPointer.element }?.let { elementToSearch ->
                                    queryParameters.optimizer.searchWord(
                                        name, effectiveSearchScope, UsageSearchContext.IN_CODE, true, elementToSearch, resultProcessor
                                    )
                                }
                            }
                        }
                    }

                    classNameForCompanionObject?.let { name ->
                        longTasks.add {
                            runReadAction { elementToSearchPointer.element }?.let { elementToSearch ->
                                queryParameters.optimizer.searchWord(
                                    name, effectiveSearchScope, UsageSearchContext.ANY, true, elementToSearch, resultProcessor
                                )
                            }
                        }
                    }
                }


                if (elementToSearchPointer.element is KtParameter && kotlinOptions.searchNamedArguments) {
                    longTasks.add {
                        DumbService.getInstance(queryParameters.project).runReadActionInSmartMode(Runnable {
                            elementToSearchPointer.element.safeAs<KtParameter>()?.let(::searchNamedArguments)
                        })
                    }
                }

                if (!(elementToSearchPointer.element is KtElement && runReadAction { isOnlyKotlinSearch(effectiveSearchScope) })) {
                    longTasks.add {
                        DumbService.getInstance(queryParameters.project).runReadActionInSmartMode(Runnable {
                            element?.element?.let(::searchLightElements)
                        })
                    }
                }

                element?.element?.takeIf { it is KtFunction || it is PsiMethod }?.let { _ ->
                    element?.element?.let {
                        OperatorReferenceSearcher.create(
                            it, effectiveSearchScope, consumer, queryParameters.optimizer, kotlinOptions
                        )
                    }
                        ?.let { searcher ->
                            longTasks.add { searcher.run() }
                        }
                }

                element?.element?.takeIf { (it as? KtConstructor<*>)?.containingClass()?.isEnum() == true }?.let { el ->
                    val klass = (el as KtConstructor<*>).containingClass() as KtClass
                    if (!effectiveSearchScope.contains(klass.containingFile.virtualFile)) return@let
                    klass.declarations.filterIsInstance<KtEnumEntry>().forEach { enumEntry ->
                        enumEntry.descendantsOfType<KtEnumEntrySuperclassReferenceExpression>().forEach { superEntry ->
                            val target = analyze(superEntry) {
                                superEntry.resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.psi
                            }
                            if (target != el &&
                                !(kotlinOptions.acceptOverloads && target is KtConstructor<*> && target.containingClass() == klass)
                            ) {
                                return@forEach
                            }

                            consumer.process(object : PsiReferenceBase<KtEnumEntrySuperclassReferenceExpression>(superEntry) {
                                override fun resolve(): PsiElement = el
                                override fun getRangeInElement(): TextRange {
                                    return TextRange(0, superEntry.textLength)
                                }
                                override fun handleElementRename(newElementName: String): PsiElement = superEntry
                            })
                        }
                    }

                }
            }

            if (kotlinOptions.searchForComponentConventions) {
                element?.let(::searchForComponentConventions)
            }
        }

        private fun searchNamedArguments(parameter: KtParameter) {
            val parameterName = parameter.name ?: return
            val function = parameter.ownerFunction as? KtFunction ?: return
            if (function.nameAsName?.isSpecial != false) return
            val project = function.project
            var namedArgsScope = function.useScope.intersectWith(queryParameters.scopeDeterminedByUser)

            if (namedArgsScope is GlobalSearchScope) {
                namedArgsScope = KotlinSourceFilterScope.everything(namedArgsScope, project)

                val filesWithFunctionName = CacheManager.getInstance(project).getVirtualFilesWithWord(
                    function.name!!, UsageSearchContext.IN_CODE, namedArgsScope, true
                )
                namedArgsScope = GlobalSearchScope.filesScope(project, filesWithFunctionName.asList())
            }

            val processor = KotlinRequestResultProcessor(parameter, filter = { it.isNamedArgumentReference() })
            queryParameters.optimizer.searchWord(
                parameterName,
                namedArgsScope,
                KOTLIN_NAMED_ARGUMENT_SEARCH_CONTEXT,
                true,
                parameter,
                processor
            )
        }

        @RequiresReadLock
        private fun searchLightElements(element: PsiElement) {
            val project = element.project
            when (element) {
                is KtClassOrObject -> {
                    processKtClassOrObject(element)
                }

                is KtConstructor<*> -> {
                    val psiMethods = findAllRelatedActualsOrSelf(element)
                        .filterIsInstance<KtConstructor<*>>()
                        .flatMap { getLightClassMethods(it) }

                    psiMethods.forEach { psiMethod ->
                        val pointer = psiMethod.createSmartPointer()
                        longTasks.add {
                            runReadAction { pointer.element }?.let {
                                MethodReferencesSearch.search(
                                    it,
                                    queryParameters.effectiveSearchScope.excludeFileTypes(
                                        project, KotlinFileType.INSTANCE
                                    ),
                                    true
                                ).forEach(consumer)
                            }
                        }
                    }
                }

                is KtNamedFunction -> {
                    element.name?.let { getLightClassMethods(element).forEach(::searchNamedElement) }

                    processStaticsFromCompanionObject(element)
                }

                is KtProperty -> {
                    val propertyAccessors = findAllRelatedActualsOrSelf(element)
                            .filterIsInstance<KtProperty>()
                            .map { getLightClassPropertyMethods(it) }
                    propertyAccessors.forEach { propertyAccessor ->
                        propertyAccessor.allDeclarations.forEach(::searchNamedElement)
                    }
                    processStaticsFromCompanionObject(element)
                }

                is KtParameter -> {
                    searchPropertyAccessorMethods(element)
                    if (element.getStrictParentOfType<KtPrimaryConstructor>() != null) {
                        // Simple parameters without val and var shouldn't be processed here because of local search scope
                        val parameterDeclarations = getLightClassPropertyMethods(element).allDeclarations
                        parameterDeclarations.filterDataClassComponentsIfDisabled(kotlinOptions).forEach(::searchNamedElement)
                    }
                }

                is KtLightMethod -> {
                    val declaration = element.kotlinOrigin
                    if (declaration is KtProperty || (declaration is KtParameter && declaration.hasValOrVar())) {
                        searchNamedElement(declaration as PsiNamedElement)
                        processStaticsFromCompanionObject(declaration)
                    } else if (declaration is KtPropertyAccessor) {
                        val property = declaration.getStrictParentOfType<KtProperty>()
                        searchNamedElement(property)
                    } else if (declaration is KtFunction) {
                        processStaticsFromCompanionObject(declaration)
                        if (element.isMangled) {
                            searchNamedElement(declaration) { it.restrictToKotlinSources() }
                        }
                    }
                }

                is KtLightParameter -> {
                    val origin = element.kotlinOrigin ?: return
                    searchPropertyAccessorMethods(origin)
                }
            }
        }

        @RequiresReadLock
        private fun searchPropertyAccessorMethods(origin: KtParameter) {
            val lightMethods = findAllRelatedActualsOrSelf(origin)
                .filterIsInstance<KtParameter>()
                .flatMap { it.toLightElements() }
                .toList()
            val namedElements = lightMethods.filterDataClassComponentsIfDisabled(kotlinOptions)
            for (element in namedElements) {
                searchMethodAware(element)
            }
        }

        @RequiresReadLock
        private fun searchMethodAware(element: PsiNamedElement) {
            if (element is PsiMethod) {
                val pointer = element.createSmartPointer()
                longTasks.add {
                    runReadAction { pointer.element }?.let {
                        MethodReferencesSearch.search(it, queryParameters.effectiveSearchScope, true).forEach(consumer)
                    }
                }
            } else {
                searchNamedElement(element)
            }
        }

        /**
         * return self if [element] is not expect nor actual
         */
        @RequiresReadLock
        private fun findAllRelatedActualsOrSelf(element: KtDeclaration): Set<KtDeclaration> {
            val expectActualSupport = ExpectActualSupport.getInstance(element.project)
            return when {
                element.isExpectDeclaration() -> expectActualSupport.actualsForExpect(element)
                !kotlinOptions.searchForExpectedUsages -> setOf(element)
                else -> {
                    val expectDeclaration = expectActualSupport.expectDeclarationIfAny(element)
                    when (expectDeclaration) {
                        null -> setOf(element)
                        else -> expectActualSupport.actualsForExpect(expectDeclaration)
                    }
                }
            }
        }

        @RequiresReadLock
        private fun processKtClassOrObject(element: KtClassOrObject) {
            if (element.name == null) return

            val lightClasses = findAllRelatedActualsOrSelf(element).mapNotNull { (it as? KtClassOrObject)?.toLightClass() }

            lightClasses.forEach(::searchNamedElement)

            if (element is KtObjectDeclaration && element.isCompanion()) {
                getLightFieldForCompanionObject(element)?.let(::searchNamedElement)

                if (kotlinOptions.acceptCompanionObjectMembers) {
                    val originLightClass = element.getStrictParentOfType<KtClass>()?.toLightClass()
                    if (originLightClass != null) {
                        val lightDeclarations: List<KtLightMember<*>?> =
                            originLightClass.methods.map { it as? KtLightMethod } + originLightClass.fields.map { it as? KtLightField }

                        for (declaration in element.declarations) {
                            lightDeclarations
                                .firstOrNull { it?.kotlinOrigin == declaration }
                                ?.let(::searchNamedElement)
                        }
                    }
                }
            }
        }

        private fun searchForComponentConventions(elementPointer: SmartPsiElementPointer<PsiElement>) {
            DumbService.getInstance(queryParameters.project).runReadActionInSmartMode(Runnable {
                when (val element = elementPointer.element) {
                    is KtParameter -> {
                        val componentMethodName = element.dataClassComponentMethodName ?: return@Runnable

                        searchNamedElement(element, componentMethodName)

                        if (kotlinOptions.searchForComponentConventions) {
                            val componentIndex = element.parameterIndex()
                            val searcher = DestructuringDeclarationReferenceSearcher(
                                element,
                                componentIndex + 1,
                                queryParameters.effectiveSearchScope,
                                consumer,
                                queryParameters.optimizer,
                                kotlinOptions
                            )
                            longTasks.add { searcher.run() }
                        }
                    }

                    is KtLightParameter -> {
                        val componentMethodName = element.kotlinOrigin?.dataClassComponentMethodName ?: return@Runnable
                        val containingClass = element.method.containingClass ?: return@Runnable
                        searchDataClassComponentUsages(
                            containingClass = containingClass,
                            componentMethodName = componentMethodName,
                            kotlinOptions = kotlinOptions
                        )
                    }

                    else -> return@Runnable
                }
            })
        }

        @RequiresReadLock
        private fun searchDataClassComponentUsages(
            containingClass: KtLightClass,
            componentMethodName: String,
            kotlinOptions: KotlinReferencesSearchOptions
        ) {
            ApplicationManager.getApplication().assertReadAccessAllowed()
            containingClass.methods.firstOrNull {
                it.name == componentMethodName && it.parameterList.parametersCount == 0
            }?.let {
                searchNamedElement(it)

                OperatorReferenceSearcher.create(
                    it, queryParameters.effectiveSearchScope, consumer, queryParameters.optimizer, kotlinOptions
                )?.let { searcher -> longTasks.add { searcher.run() } }
            }
        }

        @RequiresReadLock
        private fun processStaticsFromCompanionObject(element: KtDeclaration) {
            findStaticMethodsFromCompanionObject(element).forEach(::searchNamedElement)
        }

        private fun findStaticMethodsFromCompanionObject(declaration: KtDeclaration): List<PsiMethod> {
            val originObject = declaration.parents
                .dropWhile { it is KtClassBody }
                .firstOrNull() as? KtObjectDeclaration ?: return emptyList()
            if (!originObject.isCompanion()) return emptyList()
            val originClass = originObject.getStrictParentOfType<KtClass>()
            val originLightClass = originClass?.toLightClass() ?: return emptyList()
            val allMethods = originLightClass.allMethods
            return allMethods.filter { it is KtLightMethod && it.kotlinOrigin == declaration }
        }

        @RequiresReadLock
        private fun searchNamedElement(
            element: PsiNamedElement?,
            name: String? = null,
            modifyScope: ((SearchScope) -> SearchScope)? = null
        ) {
            ApplicationManager.getApplication().assertReadAccessAllowed()
            element ?: return
            val nameToUse = name ?: element.name ?: return
            val baseScope = queryParameters.effectiveSearchScope(element)
            val scope = if (modifyScope != null) modifyScope(baseScope) else baseScope
            val context = UsageSearchContext.IN_CODE + UsageSearchContext.IN_FOREIGN_LANGUAGES + UsageSearchContext.IN_COMMENTS
            val resultProcessor = KotlinRequestResultProcessor(
                element,
                queryParameters.elementToSearch.namedUnwrappedElement ?: element,
                options = kotlinOptions
            )
            queryParameters.optimizer.searchWord(
                nameToUse,
                if (element !is KtElement) scope.excludeFileTypes(element.project, KotlinFileType.INSTANCE) else scope,
                context.toShort(),
                true,
                element,
                resultProcessor
            )
        }

        private fun PsiReference.isNamedArgumentReference(): Boolean {
            return this is KtSimpleNameReference && expression.parent is KtValueArgumentName
        }
    }
}