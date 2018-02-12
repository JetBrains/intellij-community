/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

interface GroovyFileImports {

  val file: GroovyFileBase


  val starImports: Collection<StarImport>

  val staticStarImports: Collection<StaticStarImport>

  val allNamedImports: Collection<GroovyNamedImport>


  fun processStaticImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean

  fun processAllNamedImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean

  fun processAllStarImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean

  fun processDefaultImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean


  fun isImplicit(import: GroovyImport): Boolean

  fun findUnnecessaryStatements(): Collection<GrImportStatement>

  fun findUnresolvedStatements(names: Collection<String>): Collection<GrImportStatement>
}
