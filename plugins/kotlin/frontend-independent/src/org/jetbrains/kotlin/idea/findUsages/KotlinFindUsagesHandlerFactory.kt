// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandler.NULL_HANDLER
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.OverridingMethodsSearch
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.asJava.LightClassProvider.Companion.providedToLightMethods
import org.jetbrains.kotlin.idea.findUsages.handlers.DelegatingFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinTypeParameterFindUsagesHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.isOverridable
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinFindUsagesHandlerFactory(project: Project) : FindUsagesHandlerFactory() {
    val javaHandlerFactory = JavaFindUsagesHandlerFactory(project)

    val findFunctionOptions: KotlinFunctionFindUsagesOptions = KotlinFunctionFindUsagesOptions(project)
    val findPropertyOptions = KotlinPropertyFindUsagesOptions(project)
    val findClassOptions = KotlinClassFindUsagesOptions(project)
    val defaultOptions = FindUsagesOptions(project)

    override fun canFindUsages(element: PsiElement): Boolean =
        element is KtClassOrObject ||
                element is KtNamedFunction ||
                element is KtProperty ||
                element is KtParameter ||
                element is KtTypeParameter ||
                element is KtConstructor<*> ||
                (element is KtImportAlias &&
                        // TODO: it is ambiguous case: ImportAlias does not have any reference to be resolved
                        element.importDirective?.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve() != null)


    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        when (element) {
            is KtImportAlias -> {
                return when (val resolvedElement =
                    element.importDirective?.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve()) {
                    is KtClassOrObject ->
                        if (!forHighlightUsages) {
                            createFindUsagesHandler(resolvedElement, forHighlightUsages)
                        } else NULL_HANDLER
                    is KtNamedFunction, is KtProperty, is KtConstructor<*> ->
                        createFindUsagesHandler(resolvedElement, forHighlightUsages)
                    else -> NULL_HANDLER
                }
            }

            is KtClassOrObject ->
                return KotlinFindClassUsagesHandler(element, this)

            is KtParameter -> {
                if (!forHighlightUsages) {
                    if (element.hasValOrVar()) {
                        return handlerForMultiple(element, listOf(element))
                    }
                    val function = element.ownerFunction
                    if (function != null && function.isOverridable()) {
                        val psiMethod = function.providedToLightMethods().singleOrNull()
                        if (psiMethod != null) {
                            val hasOverridden = OverridingMethodsSearch.search(psiMethod).any()
                            if (hasOverridden && findFunctionOptions.isSearchForBaseMethod) {
                                val parametersCount = psiMethod.parameterList.parametersCount
                                val parameterIndex = element.parameterIndex()
                                assert(parameterIndex < parametersCount)
                                val overridingParameters = OverridingMethodsSearch.search(psiMethod, true)
                                    .filter { it.parameterList.parametersCount == parametersCount }
                                    .mapNotNull { it.parameterList.parameters[parameterIndex].unwrapped }
                                return handlerForMultiple(element, listOf(element) + overridingParameters)
                            }
                        }

                    }
                }

                return KotlinFindMemberUsagesHandler.getInstance(element, factory = this)
            }

            is KtNamedFunction, is KtProperty, is KtConstructor<*> -> {
                val declaration = element as KtNamedDeclaration

                if (forHighlightUsages) {
                    return KotlinFindMemberUsagesHandler.getInstance(declaration, factory = this)
                }
                return handlerForMultiple(declaration, listOf(declaration))
            }

            is KtTypeParameter ->
                return KotlinTypeParameterFindUsagesHandler(element, this)

            else ->
                throw IllegalArgumentException("unexpected element type: $element")
        }
    }

    private fun handlerForMultiple(originalDeclaration: KtNamedDeclaration, declarations: Collection<PsiElement>): FindUsagesHandler {
        return when (declarations.size) {
            0 -> NULL_HANDLER

            1 -> {
                val target = declarations.single().unwrapped ?: return NULL_HANDLER
                if (target is KtNamedDeclaration) {
                    KotlinFindMemberUsagesHandler.getInstance(target, factory = this)
                } else {
                    javaHandlerFactory.createFindUsagesHandler(target, false)!!
                }
            }

            else -> DelegatingFindMemberUsagesHandler(originalDeclaration, declarations, factory = this)
        }
    }
}
