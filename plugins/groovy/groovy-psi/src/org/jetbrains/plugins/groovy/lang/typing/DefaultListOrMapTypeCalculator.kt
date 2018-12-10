// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class DefaultListOrMapTypeCalculator : GrTypeCalculator<GrListOrMap> {

  override fun getType(expression: GrListOrMap): PsiType? = when {
    expression.isMap -> getMapTypeFromDiamond(expression) ?: GrMapType.createFromNamedArgs(expression, expression.namedArguments)
    expression.isEmpty -> EmptyListLiteralType(expression)
    else -> ListLiteralType(expression)
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
}
