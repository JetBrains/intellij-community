// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.types.KotlinType
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty

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

/**
 * Almost copy-paste from [DescriptorRendererOptionsImpl]
 */
class HintsDescriptorRendererOptions : DescriptorRendererOptions {
    var hintsClassifierNamePolicy: HintsClassifierNamePolicy by property(SOURCE_CODE_QUALIFIED)

    var isLocked: Boolean = false
        private set

    fun lock() {
        check(!isLocked) { "options have been already locked to prevent mutability" }
        isLocked = true
    }

    private fun <T> property(initialValue: T): ReadWriteProperty<HintsDescriptorRendererOptions, T> {
        return Delegates.vetoable(initialValue) { _, _, _ ->
            check(!isLocked) { "Cannot modify readonly DescriptorRendererOptions" }
            true
        }
    }

    override var classifierNamePolicy: ClassifierNamePolicy by property(ClassifierNamePolicy.SOURCE_CODE_QUALIFIED)
    override var withDefinedIn by property(true)
    override var withSourceFileForTopLevel by property(true)
    override var modifiers: Set<DescriptorRendererModifier> by property(DescriptorRendererModifier.ALL_EXCEPT_ANNOTATIONS)
    override var startFromName by property(false)
    override var startFromDeclarationKeyword by property(false)
    override var debugMode by property(false)
    override var classWithPrimaryConstructor by property(false)
    override var verbose by property(false)
    override var unitReturnType by property(true)
    override var withoutReturnType by property(false)
    override var enhancedTypes by property(false)
    override var normalizedVisibilities by property(false)
    override var renderDefaultVisibility by property(true)
    override var renderDefaultModality by property(true)
    override var renderConstructorDelegation by property(false)
    override var renderPrimaryConstructorParametersAsProperties by property(false)
    override var actualPropertiesInPrimaryConstructor: Boolean by property(false)
    override var uninferredTypeParameterAsName by property(false)
    override var includePropertyConstant by property(false)
    override var withoutTypeParameters by property(false)
    override var withoutSuperTypes by property(false)
    override var typeNormalizer by property<(KotlinType) -> KotlinType> { it }
    override var defaultParameterValueRenderer by property<((ValueParameterDescriptor) -> String)?> { "..." }
    override var secondaryConstructorsAsPrimary by property(true)
    override var overrideRenderingPolicy by property(OverrideRenderingPolicy.RENDER_OPEN)
    override var valueParametersHandler: DescriptorRenderer.ValueParametersHandler by property(DescriptorRenderer.ValueParametersHandler.DEFAULT)
    override var textFormat by property(RenderingFormat.PLAIN)
    override var parameterNameRenderingPolicy by property(ParameterNameRenderingPolicy.ALL)
    override var receiverAfterName by property(false)
    override var renderCompanionObjectName by property(false)
    override var propertyAccessorRenderingPolicy by property(PropertyAccessorRenderingPolicy.DEBUG)
    override var renderDefaultAnnotationArguments by property(false)

    override var eachAnnotationOnNewLine: Boolean by property(false)

    override var excludedAnnotationClasses by property(emptySet<FqName>())

    override var excludedTypeAnnotationClasses by property(ExcludedTypeAnnotations.internalAnnotationsForResolve)

    override var annotationFilter: ((AnnotationDescriptor) -> Boolean)? by property(null)

    override var annotationArgumentsRenderingPolicy by property(AnnotationArgumentsRenderingPolicy.NO_ARGUMENTS)

    override var alwaysRenderModifiers by property(false)

    override var renderConstructorKeyword by property(true)

    override var renderUnabbreviatedType: Boolean by property(true)

    override var renderTypeExpansions: Boolean by property(false)

    override var includeAdditionalModifiers: Boolean by property(true)

    override var parameterNamesInFunctionalTypes: Boolean by property(true)

    override var renderFunctionContracts: Boolean by property(false)

    override var presentableUnresolvedTypes: Boolean by property(false)

    override var boldOnlyForNamesInHtml: Boolean by property(false)

    override var informativeErrorType: Boolean by property(true)
}
