// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template.expressions

import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.psi.codeStyle.SuggestedNameInfo

class SuggestedParameterNameExpression(private val myNameInfo: SuggestedNameInfo) : ParameterNameExpression() {
  override fun getNameInfo(context: ExpressionContext): SuggestedNameInfo {
    return myNameInfo
  }
}
