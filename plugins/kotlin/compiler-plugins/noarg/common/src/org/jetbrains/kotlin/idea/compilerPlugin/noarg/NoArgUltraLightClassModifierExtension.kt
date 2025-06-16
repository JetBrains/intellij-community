// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.asJava.UltraLightClassModifierExtension
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.asJava.classes.createGeneratedMethodFromDescriptor
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.extensions.AnnotationBasedExtension
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.idea.compilerPlugin.getAnnotationNames
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_OVERLOADS_FQ_NAME
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.util.isAnnotated
import org.jetbrains.kotlin.util.isOrdinaryClass

@InternalIgnoreDependencyViolation
private class NoArgUltraLightClassModifierExtension(project: Project) :
    AnnotationBasedExtension,
    UltraLightClassModifierExtension {

    private val cachedAnnotationsNames = CachedAnnotationNames(project, NO_ARG_ANNOTATION_OPTION_PREFIX)

    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> =
        cachedAnnotationsNames.getAnnotationNames(modifierListOwner)

    private fun isSuitableDeclaration(declaration: KtDeclaration): Boolean {
        if (getAnnotationFqNames(declaration).isEmpty()) return false

        if (!declaration.isOrdinaryClass || declaration !is KtClassOrObject) return false

        if (declaration.allConstructors.isEmpty()) return false

        if (declaration.allConstructors.any { it.getValueParameters().isEmpty() }) return false

        if (declaration.superTypeListEntries.isEmpty() && !declaration.isAnnotated) return false

        return true
    }

    override fun interceptMethodsBuilding(
        declaration: KtDeclaration,
        descriptor: Lazy<DeclarationDescriptor?>,
        containingDeclaration: KtUltraLightClass,
        methodsList: MutableList<KtLightMethod>
    ) {
        val parentClass = containingDeclaration as? KtUltraLightClass ?: return

        if (methodsList.any { it.isConstructor && it.parameters.isEmpty() }) return

        if (!isSuitableDeclaration(declaration)) return

        val descriptorValue = descriptor.value ?: return

        val classDescriptor = (descriptorValue as? ClassDescriptor)
            ?: descriptorValue.containingDeclaration as? ClassDescriptor
            ?: return

        if (!classDescriptor.hasSpecialAnnotation(declaration)) return

        if (classDescriptor.constructors.any { isZeroParameterConstructor(it) }) return

        val constructorDescriptor = createNoArgConstructorDescriptor(classDescriptor)

        methodsList.add(parentClass.createGeneratedMethodFromDescriptor(constructorDescriptor))
    }

    private fun isZeroParameterConstructor(constructor: ClassConstructorDescriptor): Boolean {
        val parameters = constructor.valueParameters.ifEmpty { return true }
        return parameters.all { it.declaresDefaultValue() } &&
                (constructor.isPrimary || constructor.annotations.hasAnnotation(JVM_OVERLOADS_FQ_NAME))
    }

    private fun createNoArgConstructorDescriptor(containingClass: ClassDescriptor): ConstructorDescriptor =
        ClassConstructorDescriptorImpl.createSynthesized(containingClass, Annotations.EMPTY, false, SourceElement.NO_SOURCE).apply {
            initialize(
                null,
                calculateDispatchReceiverParameter(),
                emptyList(),
                emptyList(),
                emptyList(),
                containingClass.builtIns.unitType,
                Modality.OPEN,
                DescriptorVisibilities.PUBLIC
            )
        }
}
