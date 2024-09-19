// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.evaluation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class ExpressionInfo @JvmOverloads constructor(
  /**
   * Text range to highlight as link,
   * will be used to compute evaluation and display text if these values not specified.
   */
  val textRange: TextRange,
  /**
   * Expression to evaluate
   */
  val expressionText: String? = null,
  val displayText: String? = expressionText,
  val element: PsiElement? = null
) 