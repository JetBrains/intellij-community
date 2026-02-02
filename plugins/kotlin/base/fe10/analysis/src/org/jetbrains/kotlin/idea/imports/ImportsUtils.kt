// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ImportsUtils")

package org.jetbrains.kotlin.idea.imports

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptorWithTypeParameters
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor
import org.jetbrains.kotlin.types.KotlinType

@K1Deprecation
@get:Deprecated("Only supported for Kotlin Plugin K1 mode. Use Kotlin Analysis API instead, which works for both K1 and K2 modes. See https://kotl.in/analysis-api and `org.jetbrains.kotlin.analysis.api.analyze` for details.")
@get:ApiStatus.ScheduledForRemoval
val DeclarationDescriptor.importableFqName: FqName?
    get() {
        if (!canBeReferencedViaImport()) return null
        return getImportableDescriptor().fqNameSafe
    }

@K1Deprecation
@Deprecated("Only supported for Kotlin Plugin K1 mode. Use Kotlin Analysis API instead, which works for both K1 and K2 modes. See https://kotl.in/analysis-api and `org.jetbrains.kotlin.analysis.api.analyze` for details.")
@ApiStatus.ScheduledForRemoval
fun DeclarationDescriptor.canBeReferencedViaImport(): Boolean {
    if (this is PackageViewDescriptor ||
        DescriptorUtils.isTopLevelDeclaration(this) ||
        this is CallableDescriptor && DescriptorUtils.isStaticDeclaration(this)
    ) {
        return !name.isSpecial
    }

    //Both TypeAliasDescriptor and ClassDescriptor
    val parentClassifier = containingDeclaration as? ClassifierDescriptorWithTypeParameters ?: return false
    if (!parentClassifier.canBeReferencedViaImport()) return false

    return when (this) {
        is ConstructorDescriptor -> !parentClassifier.isInner // inner class constructors can't be referenced via import
        is ClassDescriptor, is TypeAliasDescriptor -> true
        else -> parentClassifier is ClassDescriptor && parentClassifier.kind == ClassKind.OBJECT
    }
}

@K1Deprecation
fun DeclarationDescriptor.canBeAddedToImport(): Boolean = this !is PackageViewDescriptor && canBeReferencedViaImport()

@K1Deprecation
fun KotlinType.canBeReferencedViaImport(): Boolean {
    val descriptor = constructor.declarationDescriptor
    return descriptor != null && descriptor.canBeReferencedViaImport()
}

// for cases when class qualifier refers companion object treats it like reference to class itself
@K1Deprecation
fun KtReferenceExpression.getImportableTargets(bindingContext: BindingContext): Collection<DeclarationDescriptor> {
    val targets = bindingContext[BindingContext.SHORT_REFERENCE_TO_COMPANION_OBJECT, this]?.let { listOf(it) }
        ?: getReferenceTargets(bindingContext)
    return targets.map { it.getImportableDescriptor() }.toSet()
}

@K1Deprecation
fun ClassifierDescriptor.getConstructors(): Collection<ConstructorDescriptor> = when (this) {
    is ClassDescriptor -> constructors
    is TypeAliasDescriptor -> constructors
    else -> emptyList()
}