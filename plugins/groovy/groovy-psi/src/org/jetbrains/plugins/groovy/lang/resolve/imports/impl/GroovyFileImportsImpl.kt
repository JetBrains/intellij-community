/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:Suppress("UseExpressionBody")

package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyFileImports
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport
import org.jetbrains.plugins.groovy.lang.resolve.imports.defaultImports
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.util.flatten

internal class GroovyFileImportsImpl(
  override val file: GroovyFileBase,
  private val imports: Map<ImportKind<*>, Collection<GroovyImport>>,
  private val statementToImport: Map<GrImportStatement, GroovyImport>,
  private val importToStatement: Map<GroovyImport, GrImportStatement>
) : GroovyFileImports {

  @Suppress("UNCHECKED_CAST")
  private fun <T : GroovyImport> getImports(kind: ImportKind<T>): Collection<T> {
    val collection = imports[kind] ?: return emptyList()
    return collection as Collection<T>
  }

  private val regularImports get() = getImports(ImportKind.Regular)
  private val staticImports get() = getImports(ImportKind.Static)
  override val starImports get() = getImports(ImportKind.Star)
  override val staticStarImports get() = getImports(ImportKind.StaticStar)
  override val allNamedImports = flatten(getImports(ImportKind.Regular), getImports(ImportKind.Static))
  private val allStarImports = flatten(getImports(ImportKind.Star), getImports(ImportKind.StaticStar))

  private fun addStatement(state: ResolveState, import: GroovyImport): ResolveState {
    val statement = importToStatement[import] ?: return state
    return state.put(ClassHint.RESOLVE_CONTEXT, statement)
  }

  @Suppress("LoopToCallChain")
  private fun Collection<GroovyImport>.doProcess(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    for (import in this) {
      if (!import.processDeclarations(processor, addStatement(state, import), place, file)) return false
    }
    return true
  }

  override fun processStaticImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    return staticImports.doProcess(processor, state, place)
  }

  override fun processAllNamedImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    return allNamedImports.doProcess(processor, state, place)
  }

  override fun processAllStarImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    return allStarImports.doProcess(processor, state, place)
  }

  override fun processDefaultImports(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    return defaultImports.doProcess(processor, state, place)
  }

  override fun isImplicit(import: GroovyImport): Boolean = !importToStatement.containsKey(import)

  override fun findUnnecessaryStatements(): Collection<GrImportStatement> {
    return statementToImport.filterValues { it.isUnnecessary(this) }.keys
  }

  override fun findUnresolvedStatements(names: Collection<String>): Collection<GrImportStatement> {
    if (names.isEmpty()) return emptyList()

    val result = HashSet<GrImportStatement>()

    for (import in starImports) {
      val statement = importToStatement[import] ?: continue
      if (import.resolveImport(file) == null) {
        result += statement
      }
    }

    for (import in allNamedImports) {
      if (import.name !in names) continue
      val statement = importToStatement[import] ?: continue
      if (import.resolveImport(file) == null) {
        result += statement
      }
    }

    return result
  }

  override fun toString(): String = "Regular: ${regularImports.size}; " +
                                    "static: ${staticImports.size}; " +
                                    "*: ${starImports.size}; " +
                                    "static *: ${staticStarImports.size}"
}
