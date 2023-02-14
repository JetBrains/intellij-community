// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.*
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock

class GinqFragmentContainerBlock(blocks : List<Block>, context: FormattingContext) :
  SyntheticGroovyBlock(blocks,
                       Wrap.createWrap(WrapType.NORMAL, false),
                       Indent.getNoneIndent(),
                       Indent.getIndent(Indent.Type.NONE, true, true),
                       context) {

  override fun getSpacing(child1: Block?, child2: Block): Spacing {
    return if (child1 is GinqFragmentBlock && child2 is GinqFragmentBlock) {
      getUncertainFragmentSpacing(context.groovySettings.GINQ_GENERAL_CLAUSE_WRAP_POLICY)
    } else {
      GinqSpaces.laxSpace
    }
  }
}