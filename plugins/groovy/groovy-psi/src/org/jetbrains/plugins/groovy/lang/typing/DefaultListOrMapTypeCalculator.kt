// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType

class DefaultListOrMapTypeCalculator : GrTypeCalculator<GrListOrMap> {

  override fun getType(expression: GrListOrMap): PsiType? {
    return if (expression.isMap) {
      if (expression.isEmpty) {
        EmptyMapLiteralType(expression)
      }
      else {
        GrMapType.createFromNamedArgs(expression, expression.namedArguments)
      }
    }
    else {
      if (expression.isEmpty) {
        EmptyListLiteralType(expression)
      }
      else {
        ListLiteralType(expression)
      }
    }
  }
}
