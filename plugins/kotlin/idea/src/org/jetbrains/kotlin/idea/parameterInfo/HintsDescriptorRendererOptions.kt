// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.ClassifierNamePolicyEx
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isDefinitelyNotNullType

interface HintsClassifierNamePolicy {
    fun renderClassifier(classifier: ClassifierDescriptor, renderer: HintsTypeRenderer): String
}

/**
 * Almost copy-paste from [ClassifierNamePolicy.SOURCE_CODE_QUALIFIED]
 *
 * for local declarations qualified up to function scope
 */
object SOURCE_CODE_QUALIFIED : HintsClassifierNamePolicy {
    override fun renderClassifier(classifier: ClassifierDescriptor, renderer: HintsTypeRenderer): String =
        qualifiedNameForSourceCode(classifier)

    private fun qualifiedNameForSourceCode(descriptor: ClassifierDescriptor): String {
        val nameString = descriptor.name.render()
        if (descriptor is TypeParameterDescriptor) {
            return nameString
        }
        val qualifier = qualifierName(descriptor.containingDeclaration)
        return if (qualifier != null && qualifier != "") "$qualifier.$nameString" else nameString
    }

    private fun qualifierName(descriptor: DeclarationDescriptor): String? = when (descriptor) {
        is ClassDescriptor -> qualifiedNameForSourceCode(descriptor)
        is PackageFragmentDescriptor -> descriptor.fqName.toUnsafe().render()
        else -> null
    }
}

class HintsDescriptorRendererOptions : KotlinIdeDescriptorOptions() {
    var hintsClassifierNamePolicy: HintsClassifierNamePolicy by property(SOURCE_CODE_QUALIFIED)
}

/**
 * Almost copy-paste from [ClassifierNamePolicy.SOURCE_CODE_QUALIFIED]
 *
 * for local declarations qualified up to function scope
 */
object SourceCodeQualified: ClassifierNamePolicyEx {

    override fun renderClassifierWithType(classifier: ClassifierDescriptor, renderer: DescriptorRenderer, type: KotlinType): String =
        qualifiedNameForSourceCode(classifier, type)

    override fun renderClassifier(classifier: ClassifierDescriptor, renderer: DescriptorRenderer): String =
        qualifiedNameForSourceCode(classifier)

    private fun qualifiedNameForSourceCode(descriptor: ClassifierDescriptor, type: KotlinType? = null): String {
        val nameString = descriptor.name.render() + (type?.takeIf { it.isDefinitelyNotNullType }?.let { " & Any" } ?: "")
        if (descriptor is TypeParameterDescriptor) {
            return nameString
        }
        val qualifier = qualifierName(descriptor.containingDeclaration)
        return if (qualifier != null && qualifier != "") "$qualifier.$nameString" else nameString
    }

    private fun qualifierName(descriptor: DeclarationDescriptor): String? = when (descriptor) {
        is ClassDescriptor -> qualifiedNameForSourceCode(descriptor)
        is PackageFragmentDescriptor -> descriptor.fqName.toUnsafe().render()
        else -> null
    }
}