// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.templates

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result

class EditorConfigTemplateConstantExpression(source: Iterable<String>) : Expression() {
  private val elements = source.map(LookupElementBuilder::create).toTypedArray()
  override fun calculateResult(context: ExpressionContext): Result? = null
  override fun calculateQuickResult(context: ExpressionContext): Result? = null
  override fun calculateLookupItems(context: ExpressionContext) = elements
}
