// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiUtil.extractIterableTypeParameter
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic

private val tupleRegex = "groovy.lang.Tuple(\\d+)".toRegex()

/**
 * @return number or tuple component count of [groovy.lang.Tuple] inheritor type,
 * or `null` if the [type] doesn't represent an inheritor of [groovy.lang.Tuple] type
 */
fun getTupleComponentCountOrNull(type: PsiType): Int? {
  val classType = type as? PsiClassType
                  ?: return null
  val fqn = classType.resolve()?.qualifiedName
            ?: return null
  return tupleRegex.matchEntire(fqn)
    ?.groupValues
    ?.getOrNull(1)
    ?.toIntOrNull()
}

sealed class MultiAssignmentTypes {
  abstract fun getComponentType(position: Int): PsiType?
}

private class FixedMultiAssignmentTypes(val types: List<PsiType>) : MultiAssignmentTypes() {
  override fun getComponentType(position: Int): PsiType? = types.getOrNull(position)
}

private class UnboundedMultiAssignmentTypes(private val type: PsiType) : MultiAssignmentTypes() {
  override fun getComponentType(position: Int): PsiType = type
}

fun getMultiAssignmentType(rValue: GrExpression, position: Int): PsiType? {
  return getMultiAssignmentTypes(rValue)?.getComponentType(position)
}

fun getMultiAssignmentTypes(rValue: GrExpression): MultiAssignmentTypes? {
  if (isCompileStatic(rValue)) {
    return getMultiAssignmentTypesCS(rValue)
  }
  else {
    return getLiteralMultiAssignmentTypes(rValue)
           ?: getTupleMultiAssignmentTypes(rValue)
           ?: getIterableMultiAssignmentTypes(rValue)
  }
}

fun getMultiAssignmentTypesCountCS(rValue: GrExpression): Int? {
  return getMultiAssignmentTypesCS(rValue)?.types?.size
}

private fun getMultiAssignmentTypesCS(rValue: GrExpression): FixedMultiAssignmentTypes? {
  return getLiteralMultiAssignmentTypesCS(rValue)
         ?: getTupleMultiAssignmentTypesCS(rValue)
}

private fun getLiteralMultiAssignmentTypesCS(rValue: GrExpression): FixedMultiAssignmentTypes? {
  if (rValue !is GrListOrMap || rValue.isMap) {
    return null
  }
  return getLiteralMultiAssignmentTypes(rValue)
}

private fun getLiteralMultiAssignmentTypes(rValue: GrExpression): FixedMultiAssignmentTypes? {
  val tupleType = rValue.type as? GrTupleType ?: return null
  return FixedMultiAssignmentTypes(tupleType.componentTypes)
}

private fun getTupleMultiAssignmentTypesCS(rValue: GrExpression): FixedMultiAssignmentTypes? {
  if (GroovyConfigUtils.getInstance().getSDKVersion(rValue) < GroovyConfigUtils.GROOVY3_0) {
    return null
  }
  return getTupleMultiAssignmentTypes(rValue)
}

private fun getTupleMultiAssignmentTypes(rValue: GrExpression): FixedMultiAssignmentTypes? {
  val classType = rValue.type as? PsiClassType
                  ?: return null
  val fqn = classType.resolve()?.qualifiedName
            ?: return null
  if (!fqn.matches(tupleRegex)) {
    return null
  }
  return FixedMultiAssignmentTypes(classType.parameters.toList())
}

private fun getIterableMultiAssignmentTypes(rValue: GrExpression): MultiAssignmentTypes? {
  val iterableTypeParameter = extractIterableTypeParameter(rValue.type, false) ?: return null
  return UnboundedMultiAssignmentTypes(iterableTypeParameter)
}
