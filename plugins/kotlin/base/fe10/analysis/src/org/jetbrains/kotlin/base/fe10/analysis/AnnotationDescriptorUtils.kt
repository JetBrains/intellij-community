// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
@file:JvmName("AnnotationDescriptorUtils")
package org.jetbrains.kotlin.base.fe10.analysis

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.constants.AnnotationValue
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.reflect.KProperty

@K1Deprecation
fun Annotations.findAnnotation(annotationClass: Class<out Annotation>): AnnotationDescriptor? {
    val fqName = FqName(annotationClass.name)
    return find { it.fqName == fqName }
}

@K1Deprecation
inline fun <reified T : Annotation> Annotations.findAnnotation(): AnnotationDescriptor? = findAnnotation(T::class.java)

@K1Deprecation
fun Annotated.findAnnotation(annotationClass: Class<out Annotation>): AnnotationDescriptor? = annotations.findAnnotation(annotationClass)
@K1Deprecation
inline fun <reified T : Annotation> Annotated.findAnnotation(): AnnotationDescriptor? = findAnnotation(T::class.java)

@K1Deprecation
fun AnnotationDescriptor.getBooleanValue(name: String): Boolean? = argumentValue(name).safeAs<BooleanValue>()?.value
@K1Deprecation
fun AnnotationDescriptor.getStringValue(name: String): String? = argumentValue(name).safeAs<StringValue>()?.value
@K1Deprecation
fun AnnotationDescriptor.getAnnotationValue(name: String): AnnotationDescriptor? = argumentValue(name).safeAs<AnnotationValue>()?.value
@K1Deprecation
fun AnnotationDescriptor.getArrayValue(name: String): List<ConstantValue<*>>? = argumentValue(name).safeAs<ArrayValue>()?.value
@K1Deprecation
fun AnnotationDescriptor.getEnumValue(name: String): EnumValue? = argumentValue(name).safeAs<EnumValue>()

@K1Deprecation
fun AnnotationDescriptor.getBooleanValue(property: KProperty<*>): Boolean? = getBooleanValue(property.name)
@K1Deprecation
fun AnnotationDescriptor.getStringValue(property: KProperty<*>): String? = getStringValue(property.name)
@K1Deprecation
fun AnnotationDescriptor.getAnnotationValue(property: KProperty<*>): AnnotationDescriptor? = getAnnotationValue(property.name)
@K1Deprecation
fun AnnotationDescriptor.getArrayValue(property: KProperty<*>): List<ConstantValue<*>>? = getArrayValue(property.name)
@K1Deprecation
fun AnnotationDescriptor.getEnumValue(property: KProperty<*>): EnumValue? = getEnumValue(property.name)

@K1Deprecation
val AnnotationDescriptor.classId: ClassId?
    get() = annotationClass?.takeUnless(ErrorUtils::isError)?.classId