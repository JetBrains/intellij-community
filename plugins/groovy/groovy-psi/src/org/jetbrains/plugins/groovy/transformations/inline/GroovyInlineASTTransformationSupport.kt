// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement

/**
 * Groovy provides various ways for transforming the AST before execution.
 * Typical ways to do it are AST transformation classes (global and local) or macros.
 * If an AST transformation actually modifies some subtree, then this class can help to provide an IDE support for it.
 *
 * **Note:** This class handles modifications of the actual code that is intended to be interpreted/executed,
 * whereas [org.jetbrains.plugins.groovy.transformations.AstTransformationSupport] allows to generate additional class members.
 *
 * **See:** [Compile-time metaprogramming](https://groovy-lang.org/metaprogramming.html#_compile_time_metaprogramming)
 *
 * **See:** [Groovy macros](https://groovy-lang.org/metaprogramming.html#_macros)
 */
@ApiStatus.Experimental
interface GroovyInlineASTTransformationSupport {

  /**
   * If [transformationRoot] has a not-null [GroovyInlineASTTransformationPerformer],
   * then it is treated specially by the Groovy code insight engine. All usual highlighting, formatting and inspections are disabled for it,
   * and this work is delegated to the performer.
   *
   * Note, that the performer is responsible for the transformation of the whole subtree of [transformationRoot].
   *
   * If you are writing support for a macro, please consider resolution peculiarities
   * ([org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService]).
   */
  fun getPerformer(transformationRoot: GroovyPsiElement): GroovyInlineASTTransformationPerformer?
}