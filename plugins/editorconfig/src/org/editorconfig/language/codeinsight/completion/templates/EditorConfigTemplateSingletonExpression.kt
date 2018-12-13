// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.templates

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.TextResult

class EditorConfigTemplateSingletonExpression(private val source: String) : Expression() {
  override fun calculateResult(context: ExpressionContext) = TextResult(source)
  override fun calculateQuickResult(context: ExpressionContext) = TextResult(source)
  override fun calculateLookupItems(context: ExpressionContext) = arrayOf(LookupElementBuilder.create(source))
}
