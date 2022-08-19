// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.descriptors.annotations.BuiltInAnnotationDescriptor
import org.jetbrains.kotlin.renderer.AnnotationArgumentsRenderingPolicy
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRenderer.Companion.FQ_NAMES_IN_TYPES
import org.jetbrains.kotlin.renderer.DescriptorRendererModifier
import org.jetbrains.kotlin.renderer.OverrideRenderingPolicy
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.NewCapturedTypeConstructor
import org.jetbrains.kotlin.types.isDynamic
import org.jetbrains.kotlin.types.typeUtil.builtIns

object IdeDescriptorRenderers {
    @JvmField
    val APPROXIMATE_FLEXIBLE_TYPES: (KotlinType) -> KotlinType = {
        it.approximateFlexibleTypes(preferNotNull = false)
    }

    @JvmField
    val APPROXIMATE_FLEXIBLE_TYPES_NOT_NULL: (KotlinType) -> KotlinType = {
        it.approximateFlexibleTypes(preferNotNull = true)
    }

    val APPROXIMATE_FLEXIBLE_TYPES_FOR_COLLECTIONS: (KotlinType) -> KotlinType = {
        it.approximateFlexibleTypes(preferNotNull = true, preferUpperBoundsForCollections = true )
    }

    private fun unwrapAnonymousType(type: KotlinType): KotlinType {
        if (type.isDynamic()) return type
        if (type.constructor is NewCapturedTypeConstructor) return type

        val classifier = type.constructor.declarationDescriptor
        if (classifier != null && !classifier.name.isSpecial) return type

        type.constructor.supertypes.singleOrNull()?.let { return it }

        val builtIns = type.builtIns
        return if (type.isMarkedNullable)
            builtIns.nullableAnyType
        else
            builtIns.anyType
    }

    private val BASE: DescriptorRenderer = DescriptorRenderer.withOptions {
        normalizedVisibilities = true
        withDefinedIn = false
        renderDefaultVisibility = false
        overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OVERRIDE
        unitReturnType = false
        enhancedTypes = true
        modifiers = DescriptorRendererModifier.ALL
        renderUnabbreviatedType = false
        annotationArgumentsRenderingPolicy = AnnotationArgumentsRenderingPolicy.UNLESS_EMPTY
        annotationFilter = { it.fqName?.isRedundantJvmAnnotation != true }
    }

    @JvmField
    val SOURCE_CODE: DescriptorRenderer = BASE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SOURCE_CODE_QUALIFIED
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES(unwrapAnonymousType(it)) }
    }

    @JvmField
    val FQ_NAMES_IN_TYPES_WITH_NORMALIZER: DescriptorRenderer = FQ_NAMES_IN_TYPES.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SOURCE_CODE_QUALIFIED
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES(unwrapAnonymousType(it)) }
        renderUnabbreviatedType = false
    }

    @JvmField
    val SOURCE_CODE_TYPES: DescriptorRenderer = BASE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SOURCE_CODE_QUALIFIED
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES(unwrapAnonymousType(it)) }
        annotationFilter = { it !is BuiltInAnnotationDescriptor && it.fqName?.isRedundantJvmAnnotation != true }
        parameterNamesInFunctionalTypes = false
    }

    @JvmField
    val SOURCE_CODE_TYPES_FOR_SAM_CONVERSION: DescriptorRenderer = BASE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SOURCE_CODE_QUALIFIED
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES_FOR_COLLECTIONS(unwrapAnonymousType(it)) }
        annotationFilter = { it !is BuiltInAnnotationDescriptor && it.fqName?.isRedundantJvmAnnotation != true }
        parameterNamesInFunctionalTypes = false
    }

    @JvmField
    val SOURCE_CODE_TYPES_WITH_SHORT_NAMES: DescriptorRenderer = BASE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SHORT
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES(unwrapAnonymousType(it)) }
        modifiers = modifiers - DescriptorRendererModifier.ANNOTATIONS
        parameterNamesInFunctionalTypes = false
    }

    @JvmField
    val SOURCE_CODE_NOT_NULL_TYPE_APPROXIMATION: DescriptorRenderer = BASE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SOURCE_CODE_QUALIFIED
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES_NOT_NULL(unwrapAnonymousType(it)) }
        presentableUnresolvedTypes = true
        informativeErrorType = false
    }

    @JvmField
    val SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS: DescriptorRenderer = BASE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SHORT
        typeNormalizer = { APPROXIMATE_FLEXIBLE_TYPES(unwrapAnonymousType(it)) }
        modifiers = modifiers - DescriptorRendererModifier.ANNOTATIONS
    }
}
