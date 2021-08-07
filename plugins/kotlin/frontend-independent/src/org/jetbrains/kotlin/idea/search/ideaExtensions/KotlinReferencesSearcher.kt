// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.containers.nullize
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedGetLightClassMethods
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedGetLightClassParameterDeclarations
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedGetLightClassPropertyDeclarations
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedGetLightFieldForCompanionObject
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedToLightClass
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedToLightElements
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport.Companion.sourcesAndLibraries
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.dataClassComponentMethodName
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.expectedDeclarationIfAny
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.filterDataClassComponentsIfDisabled
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.isExpectDeclaration
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.*
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions.Companion.Empty
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions.Companion.calculateEffectiveScope
import org.jetbrains.kotlin.idea.search.usagesSearch.operators.OperatorReferenceSearcher
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
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

        internal fun calculateEffectiveScope(
            elementToSearch: PsiNamedElement,
            parameters: ReferencesSearch.SearchParameters
        ): SearchScope {
            val kotlinOptions = (parameters as? KotlinAwareReferencesSearchParameters)?.kotlinOptions ?: Empty
            val elements = if (elementToSearch is KtDeclaration && !isOnlyKotlinSearch(parameters.scopeDeterminedByUser)) {
                elementToSearch.providedToLightElements().filterDataClassComponentsIfDisabled(kotlinOptions).nullize()
            } else {
                null
            } ?: listOf(elementToSearch)

            return elements.fold(parameters.effectiveSearchScope) { scope, e ->
                scope.unionSafe(parameters.effectiveSearchScope(e))
            }
        }
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

        runReadAction {
            val element = parameters.elementToSearch

            if (!element.isValid) return@runReadAction null
            val unwrappedElement = element.namedUnwrappedElement ?: return@runReadAction null
            val name = unwrappedElement.name
            if (name == null || StringUtil.isEmptyOrSpaces(name)) return@runReadAction null
            val effectiveSearchScope = calculateEffectiveScope(unwrappedElement, parameters)

            val collector = parameters.optimizer
            val session = collector.searchSession
            val function = {
                collector.searchWord(
                    name,
                    effectiveSearchScope,
                    UsageSearchContext.IN_CODE,
                    true,
                    element,
                    AliasProcessor(element, session)
                )
            }
            function
        }?.invoke()
    }

    private class AliasProcessor(
        private val myTarget: PsiElement,
        private val mySession: SearchSession
    ) : RequestResultProcessor(myTarget) {
        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            val importStatement = element.parent as? KtImportDirective ?: return true
            val importAlias = importStatement.alias?.name ?: return true

            val reference = importStatement.importedReference?.getQualifiedElementSelector()?.mainReference ?: return true
            if (!reference.isReferenceTo(myTarget)) {
                return true
            }

            val collector = SearchRequestCollector(mySession)
            val fileScope: SearchScope = LocalSearchScope(element.containingFile)
            collector.searchWord(importAlias, fileScope, UsageSearchContext.IN_CODE, true, myTarget)
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

        private val longTasks = mutableListOf<() -> Unit>()

        fun executeLongRunningTasks() {
            longTasks.forEach {
                ProgressManager.checkCanceled()
                it()
            }
        }

        fun process() {
            var element: PsiElement? = null
            var classNameForCompanionObject: String? = null

            val (elementToSearch, effectiveSearchScope) = runReadAction {
                val psiElement = queryParameters.elementToSearch

                if (!psiElement.isValid) return@runReadAction null

                val unwrappedElement = psiElement.namedUnwrappedElement ?: return@runReadAction null

                val elementToSearch =
                    if (kotlinOptions.searchForExpectedUsages && unwrappedElement is KtDeclaration && unwrappedElement.hasActualModifier()) {
                        unwrappedElement.expectedDeclarationIfAny() as? PsiNamedElement
                    } else {
                        null
                    } ?: unwrappedElement

                val effectiveSearchScope = calculateEffectiveScope(elementToSearch, queryParameters)

                element = psiElement
                classNameForCompanionObject = elementToSearch.getClassNameForCompanionObject()

                elementToSearch to effectiveSearchScope
            } ?: return

            val psiElement = element ?: return

            val refFilter: (PsiReference) -> Boolean = when (elementToSearch) {
                is KtParameter -> ({ ref: PsiReference -> !ref.isNamedArgumentReference()/* they are processed later*/ })
                else -> ({ true })
            }

            val resultProcessor = KotlinRequestResultProcessor(elementToSearch, filter = refFilter, options = kotlinOptions)

            runReadAction {
                if (kotlinOptions.anyEnabled() || elementToSearch is KtNamedDeclaration && elementToSearch.isExpectDeclaration()) {
                    elementToSearch.name?.let {
                        // Check difference with default scope
                        longTasks.add {
                            queryParameters.optimizer.searchWord(
                                it, effectiveSearchScope, UsageSearchContext.IN_CODE, true, elementToSearch, resultProcessor
                            )
                        }
                    }
                }
            }

            classNameForCompanionObject?.let {
                queryParameters.optimizer.searchWord(
                    it, effectiveSearchScope, UsageSearchContext.ANY, true, elementToSearch, resultProcessor
                )
            }

            if (elementToSearch is KtParameter && kotlinOptions.searchNamedArguments) {
                longTasks.add { runReadAction { searchNamedArguments(elementToSearch) } }
            }

            if (!(elementToSearch is KtElement && runReadAction { isOnlyKotlinSearch(effectiveSearchScope) })) {
                longTasks.add { runReadAction { searchLightElements(psiElement) } }
            }

            if (psiElement is KtFunction || psiElement is PsiMethod) {
                runReadAction {
                    OperatorReferenceSearcher.create(
                        psiElement, effectiveSearchScope, consumer, queryParameters.optimizer, kotlinOptions
                    )
                }?.let { searcher ->
                    longTasks.add { searcher.run() }
                }
            }

            if (kotlinOptions.searchForComponentConventions) {
                searchForComponentConventions(psiElement)
            }
        }

        private fun PsiNamedElement.getClassNameForCompanionObject(): String? =
            (this is KtObjectDeclaration && this.isCompanion())
                .ifTrue { getNonStrictParentOfType<KtClass>()?.name }

        private fun searchNamedArguments(parameter: KtParameter) {
            val parameterName = parameter.name ?: return
            val function = parameter.ownerFunction as? KtFunction ?: return
            if (function.nameAsName?.isSpecial != false) return
            val project = function.project
            var namedArgsScope = function.useScope.intersectWith(queryParameters.scopeDeterminedByUser)

            if (namedArgsScope is GlobalSearchScope) {
                namedArgsScope = sourcesAndLibraries(namedArgsScope, project)

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

        private fun searchLightElements(element: PsiElement) {
            when (element) {
                is KtClassOrObject -> {
                    processKtClassOrObject(element)
                }

                is KtNamedFunction, is KtSecondaryConstructor -> {
                    val name = (element as KtFunction).name
                    if (name != null) {
                        val methods = providedGetLightClassMethods(element)
                        for (method in methods) {
                            searchNamedElement(method)
                        }
                    }

                    processStaticsFromCompanionObject(element)
                }

                is KtProperty -> {
                    val propertyDeclarations = providedGetLightClassPropertyDeclarations(element)
                    propertyDeclarations.forEach { searchNamedElement(it) }
                    processStaticsFromCompanionObject(element)
                }

                is KtParameter -> {
                    searchPropertyAccessorMethods(element)
                    if (element.getStrictParentOfType<KtPrimaryConstructor>() != null) {
                        // Simple parameters without val and var shouldn't be processed here because of local search scope
                        val parameterDeclarations = providedGetLightClassParameterDeclarations(element)
                        parameterDeclarations.filterDataClassComponentsIfDisabled(kotlinOptions).forEach { searchNamedElement(it) }
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

        private fun searchPropertyAccessorMethods(origin: KtParameter) {
            origin.providedToLightElements().filterDataClassComponentsIfDisabled(kotlinOptions).forEach { searchNamedElement(it) }
        }

        private fun processKtClassOrObject(element: KtClassOrObject) {
            val className = element.name ?: return
            val lightClass = element.providedToLightClass() ?: return
            searchNamedElement(lightClass, className)

            if (element is KtObjectDeclaration && element.isCompanion()) {
                providedGetLightFieldForCompanionObject(element)?.let { searchNamedElement(it) }

                if (kotlinOptions.acceptCompanionObjectMembers) {
                    val originLightClass = element.getStrictParentOfType<KtClass>()?.providedToLightClass()
                    if (originLightClass != null) {
                        val lightDeclarations: List<KtLightMember<*>?> =
                            originLightClass.methods.map { it as? KtLightMethod } + originLightClass.fields.map { it as? KtLightField }

                        for (declaration in element.declarations) {
                            lightDeclarations
                                .firstOrNull { it?.kotlinOrigin == declaration }
                                ?.let { searchNamedElement(it) }
                        }
                    }
                }
            }
        }

        private fun searchForComponentConventions(element: PsiElement) {
            runReadAction {
                when (element) {
                    is KtParameter -> {
                        val componentMethodName = element.dataClassComponentMethodName ?: return@runReadAction null
                        val containingClass = element.getStrictParentOfType<KtClassOrObject>()?.providedToLightClass()
                        val function = {
                            searchDataClassComponentUsages(
                                containingClass = containingClass,
                                componentMethodName = componentMethodName,
                                kotlinOptions = kotlinOptions
                            )
                        }
                        function
                    }

                    is KtLightParameter -> {
                        val componentMethodName = element.kotlinOrigin?.dataClassComponentMethodName ?: return@runReadAction null
                        val function = {
                            searchDataClassComponentUsages(
                                containingClass = element.method.containingClass,
                                componentMethodName = componentMethodName,
                                kotlinOptions = kotlinOptions
                            )
                        }
                        function
                    }

                    else -> null
                }
            }?.invoke()
        }

        private fun searchDataClassComponentUsages(
            containingClass: PsiClass?,
            componentMethodName: String,
            kotlinOptions: KotlinReferencesSearchOptions
        ) {
            runReadAction {
                containingClass?.methods?.firstOrNull {
                    it.name == componentMethodName && it.parameterList.parametersCount == 0
                }
            }?.let { componentFunction ->
                searchNamedElement(componentFunction)

                runReadAction {
                    OperatorReferenceSearcher.create(
                        componentFunction, queryParameters.effectiveSearchScope, consumer, queryParameters.optimizer, kotlinOptions
                    )
                }?.let { searcher ->
                    longTasks.add { searcher.run() }
                }
            }
        }

        private fun processStaticsFromCompanionObject(element: KtDeclaration) {
            findStaticMethodsFromCompanionObject(element).forEach { searchNamedElement(it) }
        }

        private fun findStaticMethodsFromCompanionObject(declaration: KtDeclaration): List<PsiMethod> {
            val originObject = declaration.parents
                .dropWhile { it is KtClassBody }
                .firstOrNull() as? KtObjectDeclaration ?: return emptyList()
            if (!originObject.isCompanion()) return emptyList()
            val originClass = originObject.getStrictParentOfType<KtClass>()
            val originLightClass = originClass?.providedToLightClass() ?: return emptyList()
            val allMethods = originLightClass.allMethods
            return allMethods.filter { it is KtLightMethod && it.kotlinOrigin == declaration }
        }

        private fun searchNamedElement(
            element: PsiNamedElement?,
            name: String? = null,
            modifyScope: ((SearchScope) -> SearchScope)? = null
        ) {
            element ?: return
            runReadAction {
                val nameToUse = name ?: element.name ?: return@runReadAction null
                val baseScope = queryParameters.effectiveSearchScope(element)
                val scope = if (modifyScope != null) modifyScope(baseScope) else baseScope
                val context = UsageSearchContext.IN_CODE + UsageSearchContext.IN_FOREIGN_LANGUAGES + UsageSearchContext.IN_COMMENTS
                val resultProcessor = KotlinRequestResultProcessor(
                    element,
                    queryParameters.elementToSearch.namedUnwrappedElement ?: element,
                    options = kotlinOptions
                )
                val function = { queryParameters.optimizer.searchWord(nameToUse, scope, context.toShort(), true, element, resultProcessor) }
                function
            }?.invoke()
        }

        private fun PsiReference.isNamedArgumentReference(): Boolean {
            return this is KtSimpleNameReference && expression.parent is KtValueArgumentName
        }
    }
}
