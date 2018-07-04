// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase

interface GroovyImport {

  /**
   * Resolves current import using [file] as a context
   */
  fun resolveImport(file: GroovyFileBase): PsiElement?

  /**
   * Feeds the processor with elements available via this import
   */
  fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFileBase): Boolean

  /**
   * Checks whether this import could be removed wihout any changes in resolve.
   * Example: import com.foo.Bar is unnecessary when import com.foo.* is present
   */
  fun isUnnecessary(imports: GroovyFileImports): Boolean
}

interface GroovyNamedImport : GroovyImport {

  val name: String

  val isAliased: Boolean
}

interface GroovyStarImport : GroovyImport {

  val fqn: String
}
