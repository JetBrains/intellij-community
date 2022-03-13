// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.transformations.macro.getMacroHandler

class GrMacroCompletionProvider : CompletionProvider<CompletionParameters>() {

  companion object {
    @JvmStatic
    fun register(contributor: CompletionContributor) {
      contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement(), GrMacroCompletionProvider())
    }
  }

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position
    val (macroCall, macroSupport) = getMacroHandler(position) ?: return
    val results = macroSupport.computeCompletionVariants(macroCall, parameters.offset)
    result.addAllElements(results)
  }
}