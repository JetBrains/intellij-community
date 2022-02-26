// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult


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
   * Determines if this class should handle [macroCall]
   *
   * It is guaranteed that [macroCall] is a call to a Groovy macro.
   *
   * This method should avoid the invocation of heavyweight recursion-dependent services, such as reference resolve or type inference.
   * Note, that groovy macros always win overload resolution, so if something walks like a macro, quacks like a macro,
   * then it is likely a macro.
   */
  fun isApplicable(macroCall: GrMethodCall): Boolean

  fun computeHighlighing(macroCall: GrCall) : List<HighlightInfo> = emptyList()

  fun computeType(macroCall: GrCall) : PsiType = PsiType.NULL

  fun computeCompletionVariants(macroCall: GrCall, offset: Int) : List<LookupElement> = emptyList()

  /**
   * Runs during the process of heavyweight resolve
   */
  fun processResolve(scope: PsiElement, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean = true

  /**
   * Used to mimic a synthetic "variable declaration"
   */
  fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? = null
}