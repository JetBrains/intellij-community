// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

/**
 * Class represents actual argument of method's resolve result. Either type - exactly one field is not null.
 */
data class Argument(
  /**
   * Not null if argument could not be represented as some GrExpression. At least we should know type of passed to method argument.
   */
  val type:PsiType?,
  /**
   * Not null if we know expression passed to method as argument.
   */
  val expression: GrExpression?
)