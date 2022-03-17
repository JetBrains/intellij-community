// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
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
 * **See:** `GroovyMacroTransformationSupportEx`
 *
 * **See:** [Groovy macros](https://groovy-lang.org/metaprogramming.html#_macros)
 * @see [getAvailableMacroSupport]
 *
 */
interface GroovyMacroTransformationSupport {

  /**
   * Determines if this class should handle the [macro] expansion
   *
   * It is guaranteed that [macro] is a Groovy macro.
   *
   * This method should avoid the invocation of heavyweight recursion-dependent services, such as reference resolve or type inference.
   */
  fun isApplicable(macro: PsiMethod): Boolean

  /**
   * Computes custom highlighting for the macro-expanded code.
   *
   * Since all semantic highlighting is disabled in macro-affected code,
   * the clients can provide custom keywords and type-checking errors.
   *
   * It is OK to depend on [computeType] and [computeStaticReference] here.
   *
   * **Node:** If some element within the macro is [isUntransformed], then Groovy will add its highlighting to the element.
   */
  fun computeHighlighting(macroCall: GrCall): List<HighlightInfo> = emptyList()

  /**
   * Allows to indicate that an [element] will not be transformed after [macroCall] expansion.
   * Therefore, regular Groovy code insight rules will be applied to it.
   */
  fun isUntransformed(macroCall: GrMethodCall, element: PsiElement): Boolean = false

  /**
   * Allows to tune type inference algorithms within the macro-expanded code.
   *
   * Macro expansion affects a correctly-parsed AST, and it means that the main Groovy type-checker
   * is able to successfully run in the non-expanded code. Some parts of macro-expandable code should not be typechecked by Groovy,
   * so that is where this method can be used.
   *
   * **Note:** If this method returns `null`, and [expression] is [isUntransformed],
   * then the main Groovy type-checker will handle the type of the [expression].
   */
  fun computeType(macroCall: GrMethodCall, expression: GrExpression): PsiType? = null

  /**
   * Allows to add references during the heavyweight resolve (i.e. methods and non-static-referencable variables).
   */
  fun processResolve(macroCall: GrMethodCall, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean = true

  /**
   * Allows to mimic a synthetic variable declaration. Usually reference expressions do not serve as variables, but everything can happen
   * during the macro expansion.
   *
   * Please avoid the invocation of heavyweight algorithms (plain reference resolve and typechecking) in implementation.
   * Consider using [processResolve] if you need to.
   *
   * @see [org.jetbrains.plugins.groovy.lang.resolve.markAsReferenceResolveTarget]
   */
  fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? = null
}