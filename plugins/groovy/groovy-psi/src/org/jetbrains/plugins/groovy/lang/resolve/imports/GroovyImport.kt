// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

interface GroovyImport {

  /**
   * Returns statement from which this import was created or `null`
   */
  val statement: GrImportStatement?

  /**
   * Resolves current import using [file] as a context
   */
  fun resolve(file: GroovyFile): PsiElement?

  /**
   * Feeds the processor elements available via this import
   */
  fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, file: GroovyFile): Boolean
}

interface GroovyNamedImport : GroovyImport {

  val name: String

  val isAliased: Boolean
}

interface GroovyStarImport : GroovyImport {

  val fqn: String
}
