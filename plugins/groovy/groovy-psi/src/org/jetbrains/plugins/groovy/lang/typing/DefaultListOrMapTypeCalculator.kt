// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.openapi.util.RecursionManager.doPreventingRecursion
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class DefaultListOrMapTypeCalculator : GrTypeCalculator<GrListOrMap> {

  override fun getType(expression: GrListOrMap): PsiType? {
    if (expression.isMap) {
      return getMapTypeFromDiamond(expression) ?: GrMapType.createFromNamedArgs(expression, expression.namedArguments)
    }
    else {
      return getListTypeFromDiamond(expression) ?: getTupleType(expression)
    }
  }

  private fun getMapTypeFromDiamond(expression: GrListOrMap): PsiType? {
    val namedArgs = expression.namedArguments
    if (namedArgs.isNotEmpty()) return null

    val lType = PsiImplUtil.inferExpectedTypeForDiamond(expression) ?: return null
    if (lType !is PsiClassType || !isInheritor(lType, CommonClassNames.JAVA_UTIL_MAP)) return null

    val scope = expression.resolveScope
    val facade = JavaPsiFacade.getInstance(expression.project)

    val hashMap = facade.findClass(GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP, scope) ?: return null
    return facade.elementFactory.createType(
      hashMap,
      substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 0, false),
      substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_MAP, 1, false)
    )
  }

  private fun getListTypeFromDiamond(expression: GrListOrMap): PsiType? {
    val initializers = expression.initializers
    if (initializers.isNotEmpty()) return null

    val lType = PsiImplUtil.inferExpectedTypeForDiamond(expression)
    if (lType !is PsiClassType) return null
    val scope = expression.resolveScope
    val facade = JavaPsiFacade.getInstance(expression.project)

    if (isInheritor(lType, CommonClassNames.JAVA_UTIL_LIST)) {
      val arrayList = facade.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST, scope) ?:
                      facade.findClass(CommonClassNames.JAVA_UTIL_LIST, scope) ?: return null

      return facade.elementFactory.createType(
        arrayList,
        substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_LIST, 0, false)
      )
    }
    if (isInheritor(lType, CommonClassNames.JAVA_UTIL_SET)) {
      val set = facade.findClass("java.util.LinkedHashSet", scope) ?:
                      facade.findClass(CommonClassNames.JAVA_UTIL_SET, scope) ?: return null

      return facade.elementFactory.createType(
        set,
        substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_SET, 0, false)
      )

    }
    return null
  }

  private fun getTupleType(expression: GrListOrMap): PsiType? {
    val initializers = expression.initializers

    return object : GrTupleType(expression.resolveScope, JavaPsiFacade.getInstance(expression.project)) {

      override fun inferComponents(): Array<PsiType?> {
        return initializers.flatMap {
          doGetComponentTypes(it) ?: return PsiType.EMPTY_ARRAY
        }.toTypedArray()
      }

      private fun doGetComponentTypes(initializer: GrExpression): Collection<PsiType>? {
        return doPreventingRecursion(initializer, false) {
          if (initializer is GrSpreadArgument) {
            (initializer.argument.type as? GrTupleType)?.componentTypes?.toList()
          }
          else {
            TypesUtil.boxPrimitiveType(initializer.type, initializer.manager, initializer.resolveScope)?.let { listOf(it) }
          }
        }
      }

      override fun isValid(): Boolean = initializers.all { it.isValid }
    }
  }
}
