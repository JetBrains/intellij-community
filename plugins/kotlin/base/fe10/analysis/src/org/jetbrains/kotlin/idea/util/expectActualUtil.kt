// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.allImplementingDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.multiplatform.*

fun MemberDescriptor.expectedDescriptors(): List<DeclarationDescriptor> {
    val expectedCompatibilityMap = ExpectedActualResolver.findExpectedForActual(this)
        ?: return emptyList()

    return expectedCompatibilityMap[ExpectActualCompatibility.Compatible]
        ?: expectedCompatibilityMap.values.flatten()
}

// TODO: Sort out the cases with multiple expected descriptors
fun MemberDescriptor.expectedDescriptor(): DeclarationDescriptor? {
    return expectedDescriptors().firstOrNull()
}

fun KtDeclaration.expectedDeclarationIfAny(): KtDeclaration? {
    val expectedDescriptor = (resolveToDescriptorIfAny() as? MemberDescriptor)?.expectedDescriptor() ?: return null
    return DescriptorToSourceUtils.descriptorToDeclaration(expectedDescriptor) as? KtDeclaration
}

fun DeclarationDescriptor.liftToExpected(): DeclarationDescriptor? {
    if (this is MemberDescriptor) {
        return when {
            isExpect -> this
            isActual -> expectedDescriptor()
            else -> null
        }
    }

    if (this is ValueParameterDescriptor) {
        val containingExpectedDescriptor = containingDeclaration.liftToExpected() as? CallableDescriptor ?: return null
        return containingExpectedDescriptor.valueParameters.getOrNull(index)
    }

    return null
}

fun KtDeclaration.liftToExpected(): KtDeclaration? {
    val descriptor = resolveToDescriptorIfAny()
    val expectedDescriptor = descriptor?.liftToExpected() ?: return null
    return DescriptorToSourceUtils.descriptorToDeclaration(expectedDescriptor) as? KtDeclaration
}

fun KtParameter.liftToExpected(): KtParameter? {
    val parameterDescriptor = resolveToParameterDescriptorIfAny()
    val expectedDescriptor = parameterDescriptor?.liftToExpected() ?: return null
    return DescriptorToSourceUtils.descriptorToDeclaration(expectedDescriptor) as? KtParameter
}

fun KtDeclaration.withExpectedActuals(): List<KtDeclaration> {
    val expect = liftToExpected() ?: return listOf(this)
    val actuals = expect.actualsForExpected()
    return listOf(expect) + actuals
}

fun ModuleDescriptor.hasActualsFor(descriptor: MemberDescriptor) =
    descriptor.findActualInModule(this).isNotEmpty()

private fun MemberDescriptor.findActualInModule(
    module: ModuleDescriptor,
    checkCompatible: Boolean = false
): List<DeclarationDescriptor> =
    if (checkCompatible) {
        findCompatibleActualsForExpected(module, onlyFromThisModule(module))
    } else {
        findAnyActualsForExpected(module, onlyFromThisModule(module))
    }.filter { (it as? MemberDescriptor)?.isEffectivelyActual() == true }

private fun MemberDescriptor.isEffectivelyActual(checkConstructor: Boolean = true): Boolean =
    isActual || isEnumEntryInActual() || isConstructorInActual(checkConstructor)

private fun MemberDescriptor.isConstructorInActual(checkConstructor: Boolean) =
    checkConstructor && this is ClassConstructorDescriptor && containingDeclaration.isEffectivelyActual(checkConstructor = true)

private fun MemberDescriptor.isEnumEntryInActual() =
    (DescriptorUtils.isEnumEntry(this) && (containingDeclaration as? MemberDescriptor)?.isActual == true)

fun DeclarationDescriptor.actualsForExpected(): Collection<DeclarationDescriptor> {
    if (this is MemberDescriptor) {
        if (!this.isExpect) return emptyList()
        return (module.allImplementingDescriptors + module).flatMap { module -> this.findActualInModule(module) }
    }

    if (this is ValueParameterDescriptor) {
        return containingDeclaration.actualsForExpected().mapNotNull { (it as? CallableDescriptor)?.valueParameters?.getOrNull(index) }
    }

    return emptyList()
}

fun KtDeclaration.hasAtLeastOneActual() = actualsForExpected().isNotEmpty()

// null means "any platform" here
fun KtDeclaration.actualsForExpected(module: Module? = null): Set<KtDeclaration> =
    resolveToDescriptorIfAny(BodyResolveMode.FULL)
        ?.actualsForExpected()
        ?.filter { module == null || (it.module.getCapability(ModuleInfo.Capability) as? ModuleSourceInfo)?.module == module }
        ?.mapNotNullTo(LinkedHashSet()) {
            DescriptorToSourceUtils.descriptorToDeclaration(it) as? KtDeclaration
        } ?: emptySet()

fun KtDeclaration.isExpectDeclaration(): Boolean {
    return when {
        hasExpectModifier() -> true
        else -> containingClassOrObject?.isExpectDeclaration() == true
    }
}

fun KtDeclaration.hasMatchingExpected() = (resolveToDescriptorIfAny() as? MemberDescriptor)?.expectedDescriptor() != null

fun KtDeclaration.isEffectivelyActual(checkConstructor: Boolean = true): Boolean = when {
    hasActualModifier() -> true
    this is KtEnumEntry || checkConstructor && this is KtConstructor<*> -> containingClass()?.hasActualModifier() == true
    else -> false
}

fun KtDeclaration.runOnExpectAndAllActuals(checkExpect: Boolean = true, useOnSelf: Boolean = false, f: (KtDeclaration) -> Unit) {
    if (hasActualModifier()) {
        val expectElement = liftToExpected()
        expectElement?.actualsForExpected()?.forEach {
            if (it !== this) {
                f(it)
            }
        }
        expectElement?.let { f(it) }
    } else if (!checkExpect || isExpectDeclaration()) {
        actualsForExpected().forEach { f(it) }
    }

    if (useOnSelf) f(this)
}

fun KtDeclaration.collectAllExpectAndActualDeclaration(withSelf: Boolean = true): Set<KtDeclaration> = when {
    isExpectDeclaration() -> actualsForExpected()
    hasActualModifier() -> liftToExpected()?.let { it.actualsForExpected() + it - this }.orEmpty()
    else -> emptySet()
}.let { if (withSelf) it + this else it }

fun KtDeclaration.runCommandOnAllExpectAndActualDeclaration(
    @NlsContexts.Command command: String = "",
    writeAction: Boolean = false,
    withSelf: Boolean = true,
    f: (KtDeclaration) -> Unit
) {
    val (pointers, project) = runReadAction {
        collectAllExpectAndActualDeclaration(withSelf).map { it.createSmartPointer() } to project
    }

    fun process() {
        for (pointer in pointers) {
            val declaration = pointer.element ?: continue
            f(declaration)
        }
    }

    if (writeAction) {
        project.executeWriteCommand(command, ::process)
    } else {
        project.executeCommand(command, command = ::process)
    }
}
