// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
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
  // todo: nearly all methods accept macroCall as their first part. Maybe it is possible to curry it somehow?
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

  /**
   * Computes custom highlighting for the macro-expanded code.
   *
   * Since all semantic highlighting is disabled in macro-affected code,
   * the clients can provide custom keywords and type-checking errors.
   *
   * It is OK to depend on [computeType] and [computeStaticReference] here.
   */
  fun computeHighlighing(macroCall: GrCall) : List<HighlightInfo> = emptyList()

  /**
   * Allows to indicate that an [element] will not be transformed after macro expansion.
   * Therefore, regular Groovy code insight rules will be applied to it.
   */
  fun isUntransformed(macroCall: GrMethodCall, element: GroovyPsiElement) : Boolean = false

  /**
   * Allows to tune type inference algorithms within the macro-expanded code.
   *
   * Macro expansion affects a correctly-parsed AST, and it means that the main Groovy type-checker can successfully run in the code.
   * It is expected that macro-expansion source contains regular Groovy code that will not be transformed, but the type-checker will not
   * return sane results for it. That is where this method can be used.
   *
   * **Note:** If this method returns `null`, then the main Groovy type-checker will handle the [expression].
   */
  fun computeType(macroCall: GrMethodCall, expression: GrExpression) : PsiType? = null

  fun computeCompletionVariants(macroCall: GrCall, offset: Int) : List<LookupElement> = emptyList()

  /**
   * Runs during the process of heavyweight resolve
   */
  fun processResolve(macroCall: GrMethodCall, scope: PsiElement, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean = true

  /**
   * Used to mimic a synthetic "variable declaration"
   *
   * @see [org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.markAsReferenceResolveTarget]
   */
  fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? = null
}