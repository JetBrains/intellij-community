// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.DelegateArgumentMapping
import org.jetbrains.plugins.groovy.lang.sam.samDistance
import kotlin.math.max

fun <X : CallParameter> compare(left: ArgumentMapping<X>, right: ArgumentMapping<X>): Int {
  if (left is DelegateArgumentMapping) {
    return compare(left.delegate, right)
  }
  else if (right is DelegateArgumentMapping) {
    return compare(left, right.delegate)
  }

  if (left is NullArgumentMapping && right is NullArgumentMapping) {
    return 0
  }
  else if (left is NullArgumentMapping) {
    // prefer right
    return 1
  }
  else if (right is NullArgumentMapping) {
    // prefer left
    return -1
  }

  if (left is VarargArgumentMapping && right is VarargArgumentMapping) {
    return VarargArgumentMapping.compare(left, right)
  }
  else if (left is VarargArgumentMapping) {
    // prefer right
    return 1
  }
  else if (right is VarargArgumentMapping) {
    // prefer left
    return -1
  }

  val leftDistance = (left as PositionalArgumentMapping).distance
  val rightDistance = (right as PositionalArgumentMapping).distance
  return when {
    leftDistance == 0L -> -1
    rightDistance == 0L -> 1
    else -> leftDistance.compareTo(rightDistance) // prefer one with less distance
  }
}

fun positionalParametersDistance(map: Map<Argument, CallParameter>, context: PsiElement): Long {
  var result = 0L
  for ((argument, parameter) in map) {
    val runtimeType = argument.runtimeType ?: continue
    val parameterType = parameter.type ?: continue
    result += parameterDistance(runtimeType, argument, parameterType, context)
  }
  return result
}

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.calculateParameterDistance
 */
fun parameterDistance(argument: PsiType, argumentCompileTime: Argument?, parameter: PsiType, context: PsiElement): Long {
  return parameterDistance0(argument, argumentCompileTime, TypeConversionUtil.erasure(parameter), context)
}

private fun parameterDistance0(argument: PsiType, argumentCompileTime: Argument?, parameter: PsiType, context: PsiElement): Long {
  if (argument == parameter) return 0
  val parameterClass = (parameter as? PsiClassType)?.resolve()
  val argumentClass = (argument as? PsiClassType)?.resolve()

  if (PsiTypes.nullType() == argument) {
    return when {
      parameter is PsiPrimitiveType -> 2L shl OBJECT_SHIFT // ?
      parameterClass?.isInterface == true -> -1L
      else -> objectDistance(parameter).toLong() shl OBJECT_SHIFT
    }
  }

  if (parameterClass != null && parameterClass.isInterface) {
    val dist = getMaximumInterfaceDistance(argumentClass, parameterClass)
    if (dist > -1 || !InheritanceUtil.isInheritor(argument, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      return dist.toLong()
    }
  }

  var objectDistance: Long = 0
  val pd = getPrimitiveDistance(parameter, argument)
  if (pd != -1) {
    return pd.toLong() shl PRIMITIVE_SHIFT
  }

  objectDistance += primitives.size + 1

  if (argument is PsiArrayType && parameter !is PsiArrayType) {
    objectDistance += 4
  }

  var argumentClass2: PsiClass? = if (argument is PsiPrimitiveType) {
    JavaPsiFacade.getInstance(context.project).findClass(argument.kind.boxedFqn, context.resolveScope)
  }
  else {
    argumentClass
  }

  val samDistance = samDistance(argumentCompileTime, parameterClass)
  if (samDistance != null) {
    return (objectDistance + samDistance) shl OBJECT_SHIFT
  }
  while (argumentClass2 != null) {
    if (argumentClass2 == parameterClass) break
    if (argumentClass2.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_GSTRING && parameterClass?.qualifiedName == CommonClassNames.JAVA_LANG_STRING) {
      objectDistance += 2
      break
    }
    argumentClass2 = argumentClass2.superClass
    objectDistance += 3
  }

  return objectDistance shl OBJECT_SHIFT
}

private fun objectDistance(parameter: PsiType): Int {
  val psiTypeSuperTypes = parameter.superTypes.size
  val superTypesCount = if (parameter is PsiArrayType) psiTypeSuperTypes + 1 else psiTypeSuperTypes
  return superTypesCount * 2
}

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.PRIMITIVE_SHIFT
 */
private const val PRIMITIVE_SHIFT = 21

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.OBJECT_SHIFT
 */
private const val OBJECT_SHIFT = 23

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.PRIMITIVES
 */
private val primitives = arrayOf(
  JvmPrimitiveTypeKind.BOOLEAN.name,
  JvmPrimitiveTypeKind.BOOLEAN.boxedFqn,
  JvmPrimitiveTypeKind.BYTE.name,
  JvmPrimitiveTypeKind.BYTE.boxedFqn,
  JvmPrimitiveTypeKind.SHORT.name,
  JvmPrimitiveTypeKind.SHORT.boxedFqn,
  JvmPrimitiveTypeKind.CHAR.name,
  JvmPrimitiveTypeKind.CHAR.boxedFqn,
  JvmPrimitiveTypeKind.INT.name,
  JvmPrimitiveTypeKind.INT.boxedFqn,
  JvmPrimitiveTypeKind.LONG.name,
  JvmPrimitiveTypeKind.LONG.boxedFqn,
  GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER,
  JvmPrimitiveTypeKind.FLOAT.name,
  JvmPrimitiveTypeKind.FLOAT.boxedFqn,
  JvmPrimitiveTypeKind.DOUBLE.name,
  JvmPrimitiveTypeKind.DOUBLE.boxedFqn,
  GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL,
  CommonClassNames.JAVA_LANG_NUMBER,
  CommonClassNames.JAVA_LANG_OBJECT
)

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.PRIMITIVE_DISTANCE_TABLE
 */
private val primitiveDistances = arrayOf(
  intArrayOf(0, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2),
  intArrayOf(1, 0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2),
  intArrayOf(18, 19, 0, 1, 2, 3, 16, 17, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
  intArrayOf(18, 19, 1, 0, 2, 3, 16, 17, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
  intArrayOf(18, 19, 14, 15, 0, 1, 16, 17, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
  intArrayOf(18, 19, 14, 15, 1, 0, 16, 17, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
  intArrayOf(18, 19, 16, 17, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
  intArrayOf(18, 19, 16, 17, 14, 15, 1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 1, 0, 2, 3, 4, 5, 6, 7, 8, 9),
  intArrayOf(18, 19, 9, 10, 7, 8, 16, 17, 5, 6, 3, 4, 0, 14, 15, 12, 13, 11, 1, 2),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 0, 1, 2, 3, 4, 5, 6),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 1, 0, 2, 3, 4, 5, 6),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 0, 1, 2, 3, 4),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 1, 0, 2, 3, 4),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 3, 4, 0, 1, 2),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 3, 4, 2, 0, 1),
  intArrayOf(18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 3, 4, 2, 1, 0)
)

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.getPrimitiveIndex
 */
private fun getPrimitiveIndex(name: String?): Int = primitives.indexOf(name)

private fun getPrimitiveName(type: PsiType): String? = when (type) {
  is PsiPrimitiveType -> type.kind.name
  is PsiClassType -> type.resolve()?.qualifiedName
  else -> null
}

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.getPrimitiveDistance
 */
private fun getPrimitiveDistance(from: PsiType, to: PsiType): Int {
  val fromIndex = getPrimitiveIndex(getPrimitiveName(from))
  if (fromIndex < 0) {
    return -1
  }
  val toIndex = getPrimitiveIndex(getPrimitiveName(to))
  if (toIndex < 0) {
    return -1
  }
  return primitiveDistances[toIndex][fromIndex]
}

/**
 * @see org.codehaus.groovy.runtime.MetaClassHelper.getMaximumInterfaceDistance
 */
private fun getMaximumInterfaceDistance(argument: PsiClass?, interfaceClass: PsiClass): Int {
  if (argument == null) return -1 //?
  if (argument.isEquivalentTo(interfaceClass)) return 0

  val interfaces = argument.interfaces
  var max = -1
  for (anInterface in interfaces) {
    var sub = getMaximumInterfaceDistance(anInterface, interfaceClass)
    if (sub != -1) sub++
    max = max(max, sub)
  }
  var superClassMax = getMaximumInterfaceDistance(argument.superClass, interfaceClass)
  if (superClassMax != -1) superClassMax++
  return max(max, superClassMax)
}
