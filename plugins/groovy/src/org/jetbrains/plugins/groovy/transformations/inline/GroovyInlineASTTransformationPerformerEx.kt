// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.formatter.FormattingContext

interface GroovyInlineASTTransformationPerformerEx : GroovyInlineASTTransformationPerformer {

  /**
   * Allows to add custom code completion within the transformable code.
   *
   * By default, regular Groovy completion is used everywhere within the transformable code.
   * Use [CompletionResultSet.stopHere] if you want to get rid of it.
   */
  fun computeCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet)

  /**
   * Allows to define custom formatting rules within the transformable code.
   *
   * By default, the code under transformation root will be untouched by the formatting actions.
   *
   * **Note:** There may be parts of the transformable code within the plain Groovy one.
   * In that case, you can customize the behavior of [FormattingContext.createBlock]
   */
  fun computeFormattingBlock(node: ASTNode, context: FormattingContext): Block = context.createBlock(node, Indent.getNormalIndent(), null)
}