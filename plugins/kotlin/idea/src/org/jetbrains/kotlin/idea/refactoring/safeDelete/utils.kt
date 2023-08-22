// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.*
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import java.util.*

fun PsiElement.canDeleteElement(): Boolean {
    if (this is KtObjectDeclaration && isObjectLiteral()) return false

    if (this is KtParameter) {
        val parameterList = parent as? KtParameterList ?: return false
        val declaration = parameterList.parent as? KtDeclaration ?: return false
        return declaration !is KtPropertyAccessor
    }

    return this is KtClassOrObject
            || this is KtSecondaryConstructor
            || this is KtNamedFunction
            || this is PsiMethod
            || this is PsiClass
            || this is KtProperty
            || this is KtTypeParameter
            || this is KtTypeAlias
}

fun PsiElement.removeOverrideModifier() {
    when (this) {
        is KtNamedFunction, is KtProperty -> {
            (this as KtModifierListOwner).modifierList?.getModifier(KtTokens.OVERRIDE_KEYWORD)?.delete()
        }
        is PsiMethod -> {
            modifierList.annotations.firstOrNull { annotation ->
                annotation.qualifiedName == "java.lang.Override"
            }?.delete()
        }
    }
}

fun PsiMethod.cleanUpOverrides() {
    val superMethods = findSuperMethods(true)
    for (overridingMethod in OverridingMethodsSearch.search(this, true).findAll()) {
        val currentSuperMethods = overridingMethod.findSuperMethods(true).asSequence() + superMethods.asSequence()
        if (currentSuperMethods.all { superMethod -> superMethod.unwrapped == unwrapped }) {
            overridingMethod.unwrapped?.removeOverrideModifier()
        }
    }
}

fun checkParametersInMethodHierarchy(parameter: PsiParameter): Collection<PsiElement>? {
    val method = parameter.declarationScope as PsiMethod

    val parametersToDelete = ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(ThrowableComputable<Collection<PsiElement>?, RuntimeException> { 
            runReadAction { collectParametersHierarchy(method, parameter) }
        }, KotlinBundle.message("progress.title.collect.hierarchy", parameter.name), true, parameter.project)
    if (parametersToDelete == null || parametersToDelete.size <= 1 || isUnitTestMode()) return parametersToDelete

    val message = KotlinBundle.message("override.declaration.delete.multiple.parameters", 
                                       ElementDescriptionUtil.getElementDescription(method, RefactoringDescriptionLocation.WITHOUT_PARENT))
    val exitCode = Messages.showOkCancelDialog(parameter.project, message, IdeBundle.message("title.warning"), Messages.getQuestionIcon())
    return if (exitCode == Messages.OK) parametersToDelete else null
}

// TODO: generalize breadth-first search
private fun collectParametersHierarchy(method: PsiMethod, parameter: PsiParameter): Set<PsiElement> {
    val queue = ArrayDeque<PsiMethod>()
    val visited = HashSet<PsiMethod>()
    val parametersToDelete = HashSet<PsiElement>()

    queue.add(method)
    while (!queue.isEmpty()) {
        val currentMethod = queue.poll()

        visited += currentMethod
        addParameter(currentMethod, parametersToDelete, parameter)

        currentMethod.findSuperMethods(true)
            .filter { it !in visited }
            .forEach { queue.offer(it) }
        OverridingMethodsSearch.search(currentMethod)
            .filter { it !in visited }
            .forEach { queue.offer(it) }
    }
    return parametersToDelete
}

private fun addParameter(method: PsiMethod, result: MutableSet<PsiElement>, parameter: PsiParameter) {
    val parameterIndex = parameter.unwrapped!!.parameterIndex()

    if (method is KtLightMethod) {
        val declaration = method.kotlinOrigin
        if (declaration is KtFunction) {
            result.add(declaration.valueParameters[parameterIndex])
        }
    } else {
        result.add(method.parameterList.parameters[parameterIndex])
    }
}