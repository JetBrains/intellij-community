// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoVariantsService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.caches.project.allImplementingDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
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
    val moduleInfo = module.moduleInfo
    val expectedForActual = if (moduleInfo !is LibrarySourceInfo) {
        ExpectedActualResolver.findExpectedForActual(this)
    } else {
        val libraryVariants = moduleInfo.libraryVariantsDescriptors()
        ExpectedActualResolver.findExpectedForActual(this, { it in libraryVariants })
    }

    return expectedForActual.orEmpty().run {
        get(ExpectActualCompatibility.Compatible) ?: values.flatten()
    }
}

// TODO: Sort out the cases with multiple expected descriptors
fun MemberDescriptor.expectedDescriptor(): DeclarationDescriptor? {
    return expectedDescriptors().firstOrNull()
}

fun KtDeclaration.expectedDeclarationIfAny(): KtDeclaration? {
    val expectedDescriptor = (resolveToDescriptorIfAny() as? MemberDescriptor)?.expectedDescriptor() ?: return null
    return DescriptorToSourceUtilsIde.getAnyDeclaration(project, expectedDescriptor) as? KtDeclaration
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
): List<DeclarationDescriptor> {
    val memberDescriptors = if (checkCompatible) {
        findCompatibleActualsForExpected(module, onlyFromThisModule(module))
    } else {
        findAnyActualsForExpected(module, onlyFromThisModule(module))
    }

    return memberDescriptors.filter {
        // actual modifiers aren't present in library binaries, so we skip this check for them
        it.isEffectivelyActual() || module.moduleInfo is BinaryModuleInfo
    }
}

private fun MemberDescriptor.isEffectivelyActual(checkConstructor: Boolean = true): Boolean =
    isActual || isEnumEntryInActual() || isConstructorInActual(checkConstructor)

private fun MemberDescriptor.isConstructorInActual(checkConstructor: Boolean) =
    checkConstructor && this is ClassConstructorDescriptor && containingDeclaration.isEffectivelyActual(checkConstructor = true)

private fun MemberDescriptor.isEnumEntryInActual() =
    (DescriptorUtils.isEnumEntry(this) && (containingDeclaration as? MemberDescriptor)?.isActual == true)

fun DeclarationDescriptor.actualsForExpected(): Collection<DeclarationDescriptor> {
    if (this is MemberDescriptor) {
        if (!this.isExpect) return emptyList()
        val moduleInfo = module.moduleInfo
        return buildList {
            if (moduleInfo !is LibrarySourceInfo) {
                addAll(module.allImplementingDescriptors)
                add(module)
            } else {
                // filter out libraries based on intermediate source-sets because for them
                // we can't properly navigate to the selected actual source code later
                addAll(moduleInfo.libraryVariantsDescriptors(onlyPlatformVariants = true))
            }
        }.flatMap { findActualInModule(it) }
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
        .orEmpty()
        .filter { module == null || (it.module.getCapability(ModuleInfo.Capability) as? ModuleSourceInfo)?.module == module }
        .mapNotNull {
            DescriptorToSourceUtilsIde.getAnyDeclaration(moduleInfo.project, it) as? KtDeclaration
        }
        .toSet()


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

private fun LibrarySourceInfo.libraryVariantsDescriptors(onlyPlatformVariants: Boolean = false): List<ModuleDescriptor> {
    val binariesModuleInfo = binariesModuleInfo as? LibraryInfo ?: return emptyList()
    return LibraryInfoVariantsService.getInstance(project)
        .variants(binariesModuleInfo)
        .filter { !onlyPlatformVariants || it.isPlatformVariant() }
        .mapNotNull {
            KotlinCacheService.getInstance(project)
                .getResolutionFacadeByModuleInfo(it, it.platform)?.moduleDescriptor
        }
}

private fun LibraryInfo.isPlatformVariant() = platform.size == 1 && platform.first() !is NativePlatformUnspecifiedTarget