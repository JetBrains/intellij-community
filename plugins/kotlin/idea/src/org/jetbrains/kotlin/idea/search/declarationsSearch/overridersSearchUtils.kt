// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.declarationsSearch

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.isOverridable
import org.jetbrains.kotlin.asJava.classes.KtFakeLightMethod
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.core.getDirectlyOverriddenDeclarations
import org.jetbrains.kotlin.idea.refactoring.resolveToExpectedDescriptorIfPossible
import org.jetbrains.kotlin.idea.search.excludeKotlinSources
import org.jetbrains.kotlin.idea.search.useScope
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.substitutions.getTypeSubstitutor
import org.jetbrains.kotlin.util.findCallableMemberBySignature

fun forEachKotlinOverride(
    ktClass: KtClass,
    members: List<KtNamedDeclaration>,
    scope: SearchScope,
    searchDeeply: Boolean,
    processor: (superMember: PsiElement, overridingMember: PsiElement) -> Boolean
): Boolean {
    val baseClassDescriptor = runReadAction { ktClass.unsafeResolveToDescriptor() as ClassDescriptor }
    val baseDescriptors =
        runReadAction { members.mapNotNull { it.unsafeResolveToDescriptor() as? CallableMemberDescriptor }.filter { it.isOverridable } }
    if (baseDescriptors.isEmpty()) return true

    HierarchySearchRequest(ktClass, scope, searchDeeply).searchInheritors().forEach(Processor { psiClass ->
        val inheritor = psiClass.unwrapped as? KtClassOrObject ?: return@Processor true
        runReadAction {
            val inheritorDescriptor = inheritor.unsafeResolveToDescriptor() as ClassDescriptor
            val substitutor =
                getTypeSubstitutor(baseClassDescriptor.defaultType, inheritorDescriptor.defaultType) ?: return@runReadAction true
            baseDescriptors.forEach {
                val superMember = it.source.getPsi()!!
                val overridingDescriptor = (it.substitute(substitutor) as? CallableMemberDescriptor)?.let { memberDescriptor ->
                    inheritorDescriptor.findCallableMemberBySignature(memberDescriptor)
                }
                val overridingMember = overridingDescriptor?.source?.getPsi()
                if (overridingMember != null) {
                    if (!processor(superMember, overridingMember)) return@runReadAction false
                }
            }
            true
        }
    })

    return true
}

fun PsiMethod.forEachOverridingMethod(
    scope: SearchScope = runReadAction { useScope() },
    processor: (PsiMethod) -> Boolean
): Boolean {
    if (this !is KtFakeLightMethod) {
        if (!OverridingMethodsSearch.search(this, scope.excludeKotlinSources(), true).forEach(Processor { processor(it) })) return false
    }

    val ktMember = this.unwrapped as? KtNamedDeclaration ?: return true
    val ktClass = runReadAction { ktMember.containingClassOrObject as? KtClass } ?: return true
    return forEachKotlinOverride(ktClass, listOf(ktMember), scope, searchDeeply = true) { _, overrider ->
        val lightMethods = runReadAction { overrider.toPossiblyFakeLightMethods().distinctBy { it.unwrapped } }
        lightMethods.all { processor(it) }
    }
}

fun findDeepestSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> {
    return when (val element = method.unwrapped) {
        is PsiMethod -> element.findDeepestSuperMethods().toList()
        is KtCallableDeclaration -> {
            val descriptor = element.resolveToDescriptorIfAny() as? CallableMemberDescriptor ?: return emptyList()
            descriptor.getDeepestSuperDeclarations(false).mapNotNull {
                it.source.getPsi() ?: DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, it)
            }
        }
        else -> emptyList()
    }
}

fun findSuperDescriptors(declaration: KtDeclaration, descriptor: DeclarationDescriptor): Sequence<DeclarationDescriptor> {
    val sequenceOfExpectedDescriptor = declaration.takeIf { it.hasActualModifier() }
        ?.resolveToExpectedDescriptorIfPossible()
        ?.let { sequenceOf(it) }
        .orEmpty()

    val superDescriptors = findSuperDescriptors(descriptor)
    return if (superDescriptors == null) sequenceOfExpectedDescriptor else superDescriptors + sequenceOfExpectedDescriptor
}

private fun findSuperDescriptors(descriptor: DeclarationDescriptor): Sequence<DeclarationDescriptor>? {
    val superDescriptors: Collection<DeclarationDescriptor> = when (descriptor) {
        is ClassDescriptor -> {
            val supertypes = descriptor.typeConstructor.supertypes
            val superclasses = supertypes.mapNotNull { type ->
                type.constructor.declarationDescriptor as? ClassDescriptor
            }

            ContainerUtil.removeDuplicates(superclasses)
            superclasses
        }

        is CallableMemberDescriptor -> descriptor.getDirectlyOverriddenDeclarations()
        else -> return null
    }

    return superDescriptors.asSequence().filterNot { it is ClassDescriptor && KotlinBuiltIns.isAny(it) }
}

fun findSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> {
    return when (val element = method.unwrapped) {
        is PsiMethod -> element.findSuperMethods().toList()
        is KtCallableDeclaration -> {
            val descriptor = element.resolveToDescriptorIfAny() as? CallableMemberDescriptor ?: return emptyList()
            descriptor.getDirectlyOverriddenDeclarations().mapNotNull {
                it.source.getPsi() ?: DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, it)
            }
        }
        else -> emptyList()
    }
}

fun findOverridingMethodsInKotlin(
    parentClass: PsiClass,
    baseElement: PsiNamedElement,
    parameters: OverridingMethodsSearch.SearchParameters,
    consumer: Processor<in PsiMethod>,
): Boolean = ClassInheritorsSearch.search(parentClass, parameters.scope, true).forEach(Processor { inheritor: PsiClass ->
    val found = runReadAction { findOverridingMethod(inheritor, baseElement) }

    found == null || (consumer.process(found) && parameters.isCheckDeep)
})

private fun findOverridingMethod(inheritor: PsiClass, baseElement: PsiNamedElement): PsiMethod? {
    // Leave Java classes search to JavaOverridingMethodsSearcher
    if (inheritor !is KtLightClass) return null

    val name = baseElement.name
    val methodsByName = inheritor.findMethodsByName(name, false)

    for (lightMethodCandidate in methodsByName) {
        val candidateDescriptor = (lightMethodCandidate as? KtLightMethod)?.kotlinOrigin?.unsafeResolveToDescriptor() ?: continue
        if (candidateDescriptor !is CallableMemberDescriptor) continue

        val overriddenDescriptors = candidateDescriptor.getDirectlyOverriddenDeclarations()
        for (candidateSuper in overriddenDescriptors) {
            val candidateDeclaration = DescriptorToSourceUtils.descriptorToDeclaration(candidateSuper)
            if (candidateDeclaration == baseElement) {
                return lightMethodCandidate
            }
        }
    }

    return null
}
