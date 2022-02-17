// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall


/**
 * Allows to handle `@org.codehaus.groovy.macro.runtime.Macro` method expansions in the IDE.
 * Groovy macros transform regular Groovy AST in an unpredictable way, so general code insight is useless and even harmful there.
 *
 * Inheritors of this interface can provide custom support for macros. It may seem like a lightweight (or ad-hoc, if you wish) language injection.
 *
 * **See:** [Groovy macros](https://groovy-lang.org/metaprogramming.html#_macros)
 */
interface GroovyMacroTransformationSupport {

  /**
   * It is guaranteed that [macroCall] is a call to Groovy macros.
   */
  fun isApplicable(macroCall: GrCall): Boolean

  fun getHighlighing(macroCall: GrCall) : List<HighlightInfo> = emptyList()

  fun getType(macroCall: GrCall) : PsiType = PsiType.NULL

  fun getCompletionVariants(macroCall: GrCall, offset: Int) : List<LookupElement> = emptyList()
}