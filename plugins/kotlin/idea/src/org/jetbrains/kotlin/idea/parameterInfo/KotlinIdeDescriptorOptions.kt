// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.types.KotlinType
import java.lang.reflect.Modifier
import kotlin.jvm.internal.PropertyReference1Impl
import kotlin.properties.Delegates
import kotlin.properties.ObservableProperty
import kotlin.properties.ReadWriteProperty


/**
 * Almost copy-paste from [DescriptorRendererOptionsImpl]
 */
open class KotlinIdeDescriptorOptions : DescriptorRendererOptions {

    var isLocked: Boolean = false
        private set

    fun lock() {
        check(!isLocked) { "options have been already locked to prevent mutability" }
        isLocked = true
    }

    protected fun <T> property(initialValue: T): ReadWriteProperty<KotlinIdeDescriptorOptions, T> {
        return Delegates.vetoable(initialValue) { _, _, _ ->
            check(!isLocked) { "Cannot modify readonly DescriptorRendererOptions" }
            true
        }
    }

    fun copy(): KotlinIdeDescriptorOptions {
        val copy = KotlinIdeDescriptorOptions()

        //TODO: use Kotlin reflection
        for (field in this::class.java.declaredFields) {
            if (field.modifiers.and(Modifier.STATIC) != 0) continue
            field.isAccessible = true
            val property = field.get(this) as? ObservableProperty<*> ?: continue
            assert(!field.name.startsWith("is")) { "Fields named is* are not supported here yet" }
            val value = property.getValue(
                this,
                PropertyReference1Impl(KotlinIdeDescriptorOptions::class, field.name, "get" + field.name.capitalize())
            )
            field.set(copy, copy.property(value))
        }

        return copy
    }

    var bold by property(false)
    var dataClassWithPrimaryConstructor by property(true)
    var doNotExpandStandardJavaTypeAliases by property(true)
    var highlightingManager by property(KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING)

    override var classifierNamePolicy: ClassifierNamePolicy by property(SourceCodeQualified)
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
    override var propertyConstantRenderer: ((ConstantValue<*>) -> String?)? by property(null)
    override var renderDefaultAnnotationArguments by property(false)
    override var eachAnnotationOnNewLine by property(false)
    override var excludedAnnotationClasses by property(emptySet<FqName>())
    override var excludedTypeAnnotationClasses by property(ExcludedTypeAnnotations.internalAnnotationsForResolve)
    override var annotationFilter: ((AnnotationDescriptor) -> Boolean)? by property(null)
    override var annotationArgumentsRenderingPolicy by property(AnnotationArgumentsRenderingPolicy.NO_ARGUMENTS)
    override var alwaysRenderModifiers by property(false)
    override var renderConstructorKeyword by property(true)
    override var renderUnabbreviatedType by property(true)
    override var renderTypeExpansions by property(false)
    override var includeAdditionalModifiers by property(true)
    override var parameterNamesInFunctionalTypes by property(true)
    override var renderFunctionContracts by property(false)
    override var presentableUnresolvedTypes by property(false)
    override var boldOnlyForNamesInHtml by property(false)
    override var informativeErrorType by property(true)
}
