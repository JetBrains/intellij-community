// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.project.Project

class GrLineWrapPositionStrategy : GenericLineWrapPositionStrategy() {

  init {
    // Commas.
    addRule(Rule(',', WrapCondition.AFTER))

    // Symbols to wrap either before or after.
    addRule(Rule(' '))
    addRule(Rule('\t'))

    // Symbols to wrap after.
    addRule(Rule(';', WrapCondition.AFTER))
    addRule(Rule(')', WrapCondition.AFTER))

    // Symbols to wrap before
    addRule(Rule('(', WrapCondition.BEFORE))
    addRule(Rule('.', WrapCondition.AFTER))
  }

  override fun calculateWrapPosition(document: Document,
                                     project: Project?,
                                     startOffset: Int,
                                     endOffset: Int,
                                     maxPreferredOffset: Int,
                                     allowToBeyondMaxPreferredOffset: Boolean,
                                     isSoftWrap: Boolean): Int {
    var meaningfulEndOffset = endOffset - 1
    while (meaningfulEndOffset > startOffset && document.immutableCharSequence[meaningfulEndOffset].isWhitespace()) meaningfulEndOffset -= 1
    if (meaningfulEndOffset <= maxPreferredOffset) {
      // remove trailing spaces
      return -1
    }
    return super.calculateWrapPosition(document, project, startOffset, meaningfulEndOffset, maxPreferredOffset,
                                       allowToBeyondMaxPreferredOffset, isSoftWrap)
  }
}