// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.formatting.Block
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyMacroBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

interface GroovyMacroTransformationSupportEx : GroovyMacroTransformationSupport {
  /**
   * Allows to add custom completion within the macro-expandable code.
   *
   * By default, regular Groovy completion is used within the macro.
   * Consider using [CompletionResultSet.stopHere] if you want to get rid of it.
   */
  fun computeCompletionVariants(macroCall: GrMethodCall, parameters: CompletionParameters, result: CompletionResultSet)

  /**
   * Allows to define custom formatting rules within the macro-expandable code.
   *
   * By default, the code in macro will be untouched by the formatting actions.
   */
  fun computeFormattingBlock(macroCall: GrMethodCall,
                             node: ASTNode,
                             context: FormattingContext,
                             generator: GroovyBlockGenerator): Block = GroovyMacroBlock(node, context)
}