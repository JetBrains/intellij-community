// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.*
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock

class GinqFragmentContainerBlock(blocks : List<Block>, context: FormattingContext) :
  SyntheticGroovyBlock(blocks, Wrap.createWrap(WrapType.NONE, false), Indent.getContinuationIndent(), Indent.getContinuationIndent(), context) {

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    if (child1 is GinqFragmentBlock && child2 is GinqFragmentBlock) {
      return when (context.groovySettings.GINQ_GENERAL_CLAUSE_WRAP_POLICY) {
        CommonCodeStyleSettings.WRAP_ALWAYS -> Spacing.createSpacing(0, 0, 1, true, 0)
        CommonCodeStyleSettings.WRAP_AS_NEEDED -> Spacing.createSpacing(1, 1, 0, true, 0)
        CommonCodeStyleSettings.DO_NOT_WRAP -> Spacing.createSpacing(1, 1, 0, false, 0)
        else -> Spacing.createSpacing(1, 1, 0, true, 0)
      }
    }
    return super.getSpacing(child1, child2)
  }
}