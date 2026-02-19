// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult

/**
 * Allow to define custom code insight for an in-code transformation of the Groovy AST.
 *
 * Please don't rely on the lifetime of this class' instances. At each moment of time, multiple instances of [GroovyInlineASTTransformationPerformer]
 * can exist for the same AST subtree.
 */
interface GroovyInlineASTTransformationPerformer {

  /**
   * Allows to compute custom highlighting for the transformable code.
   *
   * Since all semantic highlighting is disabled in the transformable code,
   * the clients can provide custom "keywords" and "type-checking errors".
   *
   * **Node:** If some element within the transformation is [isUntransformed], then Groovy will add its regular highlighting to the element.
   */
  fun computeHighlighting(): List<HighlightInfo> = emptyList()

  /**
   * Allows to indicate that an [element] will not be modified after AST transformation.
   * Therefore, regular Groovy code insight rules will be applied to it.
   */
  fun isUntransformed(element: PsiElement): Boolean = false

  /**
   * Allows to tune type inference algorithms within the transformable code.
   *
   * A transformation affects a correctly-parsed AST, and it means that the main Groovy type-checker
   * is able to successfully run in the non-transformed code. Some parts of transformable code should not be type-checked by Groovy,
   * so that is where this method can be used.
   *
   * **Note:** If this method returns `null`, and [expression] is [isUntransformed],
   * then the main Groovy type-checker will handle the type of the [expression].
   */
  fun computeType(expression: GrExpression): PsiType? = null

  /**
   * Allows to add references during the heavyweight resolve (i.e. methods and non-static-referencable variables).
   */
  fun processResolve(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean = true

  /**
   * Allows to mimic a synthetic variable declaration. Usually reference expressions do not serve as variables, but everything can happen
   * during the transformation.
   *
   * Please avoid the invocation of heavyweight algorithms (plain reference resolve and typechecking) in implementation.
   * Consider using [processResolve] in this case.
   *
   * @see [org.jetbrains.plugins.groovy.lang.resolve.markAsReferenceResolveTarget]
   */
  fun computeStaticReference(element: PsiElement): ElementResolveResult<PsiElement>? = null
}