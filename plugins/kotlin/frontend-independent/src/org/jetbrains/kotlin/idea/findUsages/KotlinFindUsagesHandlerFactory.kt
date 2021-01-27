// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandler.NULL_HANDLER
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.findUsages.handlers.DelegatingFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinTypeParameterFindUsagesHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

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

            is KtParameter -> return if (!forHighlightUsages) handlerForMultiple(element, listOf(element))
                                     else KotlinFindMemberUsagesHandler.getInstance(element, factory = this)

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
