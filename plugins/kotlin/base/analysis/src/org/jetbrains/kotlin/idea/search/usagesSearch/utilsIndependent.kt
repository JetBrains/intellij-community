// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.usagesSearch

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.restrictByFileType
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.createConstructorHandle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contains

fun PsiElement.processDelegationCallConstructorUsages(scope: SearchScope, process: (KtCallElement) -> Boolean): Boolean {
    val task = buildProcessDelegationCallConstructorUsagesTask(scope, process)
    return task()
}

// should be executed under read-action, returns long-running part to be executed outside read-action
fun PsiElement.buildProcessDelegationCallConstructorUsagesTask(scope: SearchScope, process: (KtCallElement) -> Boolean): () -> Boolean {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val task1 = buildProcessDelegationCallKotlinConstructorUsagesTask(scope, process)
    val task2 = buildProcessDelegationCallJavaConstructorUsagesTask(scope, process)
    return { task1() && task2() }
}

fun PsiElement.buildProcessDelegationCallKotlinConstructorUsagesTask(
    scope: SearchScope,
    process: (KtCallElement) -> Boolean
): () -> Boolean {
    val element = unwrapped
    if (element != null && element !in scope) return { true }

    val elementInSource = element?.navigationElement ?: element

    val klass = when (elementInSource) {
        is KtConstructor<*> -> elementInSource.getContainingClassOrObject()
        is KtClass -> elementInSource
        else -> return { true }
    }

    if (klass !is KtClass || elementInSource !is KtDeclaration) return { true }

    val constructorHandler = createConstructorHandle(elementInSource)

    if (!processClassDelegationCallsToSpecifiedConstructor(klass, constructorHandler, process)) return { false }

    // long-running task, return it to execute outside read-action
    return { processInheritorsDelegatingCallToSpecifiedConstructor(klass, scope, constructorHandler, process) }
}

private fun PsiElement.buildProcessDelegationCallJavaConstructorUsagesTask(
    scope: SearchScope,
    process: (KtCallElement) -> Boolean
): () -> Boolean {
    if (this is KtLightElement<*, *>) return { true }
    // TODO: Temporary hack to avoid NPE while KotlinNoOriginLightMethod is around
    if (this is KtLightMethod && this.kotlinOrigin == null) return { true }
    if (!(this is PsiMethod && isConstructor)) return { true }
    val klass = containingClass ?: return { true }

    val ctorHandle = createConstructorHandle(this)
    return { processInheritorsDelegatingCallToSpecifiedConstructor(klass, scope, ctorHandle, process) }
}


private fun processInheritorsDelegatingCallToSpecifiedConstructor(
    klass: PsiElement,
    scope: SearchScope,
    constructorCallComparator: KotlinSearchUsagesSupport.ConstructorCallHandle,
    process: (KtCallElement) -> Boolean
): Boolean {
    return runReadAction { KotlinFindUsagesSupport.searchInheritors(klass, scope.restrictByFileType(KotlinFileType.INSTANCE), false) }.all {
        runReadAction {
            val unwrapped = it.takeIf { it.isValid }?.unwrapped
            if (unwrapped is KtClass)
                processClassDelegationCallsToSpecifiedConstructor(unwrapped, constructorCallComparator, process)
            else
                true
        }
    }
}

private fun processClassDelegationCallsToSpecifiedConstructor(
    klass: KtClass,
    constructorCallHandle: KotlinSearchUsagesSupport.ConstructorCallHandle,
    process: (KtCallElement) -> Boolean
): Boolean {
    if (!klass.containingKtFile.isCompiled) {
        for (secondaryConstructor in klass.secondaryConstructors) {
            val delegationCall = secondaryConstructor.getDelegationCall()
            if (constructorCallHandle.referencedTo(delegationCall)) {
                if (!process(delegationCall)) return false
            }
        }
    }
    if (!klass.isEnum()) return true
    for (declaration in klass.declarations) {
        if (declaration is KtEnumEntry) {
            val delegationCall =
                declaration.superTypeListEntries.firstOrNull() as? KtSuperTypeCallEntry
                    ?: continue

            if (constructorCallHandle.referencedTo(delegationCall.calleeExpression)) {
                if (!process(delegationCall)) return false
            }
        }
    }
    return true
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use ReferencesSearch directly to avoid light classes involvement")
fun PsiElement.searchReferencesOrMethodReferences(): Collection<PsiReference> {
    val lightMethods = toLightMethods()
    return if (lightMethods.isNotEmpty()) {
        lightMethods.flatMapTo(LinkedHashSet()) { MethodReferencesSearch.search(it).asIterable() }
    } else {
        ReferencesSearch.search(this).findAll()
    }
}