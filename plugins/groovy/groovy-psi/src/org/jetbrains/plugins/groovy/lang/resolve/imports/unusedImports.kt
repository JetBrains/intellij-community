// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyUnusedImportUtil")
@file:Suppress("LoopToCallChain")

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.openapi.util.text.StringUtil.getPackageName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.ResolveState
import com.intellij.util.containers.HashSet
import gnu.trove.THashSet
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.RegularImportHashingStrategy
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.StarImportHashingStrategy
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.defaultRegularImports
import org.jetbrains.plugins.groovy.lang.resolve.imports.impl.defaultStarImports
import org.jetbrains.plugins.groovy.lang.resolve.processClassesInFile
import org.jetbrains.plugins.groovy.lang.resolve.processClassesInPackage
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassProcessor

private val defaultRegularImportsSet = THashSet(defaultRegularImports, RegularImportHashingStrategy)
private val defaultStarImportsSet = THashSet(defaultStarImports, StarImportHashingStrategy)

fun unusedImports(file: GroovyFile): Set<GrImportStatement> {
  val result = file.validImportStatements.toMutableSet()
  result -= usedImports(file)
  return result
}

fun usedImports(file: GroovyFile): Set<GrImportStatement> {
  val imports = file.getImports()

  val (referencedStatements, unresolvedReferenceNames) = run {
    val searcher = ReferencedImportsSearcher()
    file.accept(searcher)
    searcher.results
  }
  assertShadowed(file, referencedStatements)

  val usedStatements = HashSet(referencedStatements)
  usedStatements -= imports.findUnusedStarImportStatements()
  usedStatements -= imports.findUnusedStaticImportStatements()
  usedStatements -= imports.findUnusedRegularImportStatements(file)

  if (!unresolvedReferenceNames.isEmpty()) {
    usedStatements.addAll(imports.findUnresolvedNamedImports(file, unresolvedReferenceNames))
    usedStatements.addAll(imports.findUnresolvedStarImports(file))
  }

  return usedStatements
}

private class ReferencedImportsSearcher : PsiRecursiveElementWalkingVisitor() {

  private val referencedStatements = HashSet<GrImportStatement>()
  private val unresolvedReferenceNames = LinkedHashSet<String>()

  val results: Pair<Set<GrImportStatement>, Set<String>> get() = referencedStatements to unresolvedReferenceNames

  override fun visitElement(element: PsiElement) {
    if (element !is GrImportStatement && element !is GrPackageDefinition) {
      super.visitElement(element)
    }
    if (element is GrReferenceElement<*>) {
      visitRefElement(element)
    }
  }

  private fun visitRefElement(refElement: GrReferenceElement<*>) {
    if (refElement.isQualified) return

    val refName = refElement.referenceName
    if (refName == null || "super" == refName) return

    val results = refElement.multiResolve(false)
    if (results.isEmpty()) {
      unresolvedReferenceNames.add(refName)
    }
    else {
      for (result in results) {
        val resolveContext = result.currentFileResolveContext
        if (resolveContext is GrImportStatement) {
          referencedStatements.add(resolveContext as GrImportStatement?)
        }
      }
    }
  }
}


private fun assertShadowed(file: GroovyFile, referencedStatements: Collection<GrImportStatement>) {
  val allStatements = file.validImportStatements
  val shadowedStatements = allStatements - file.getImports().allImports.getStatements()
  val intersection = referencedStatements.intersect(shadowedStatements)
  assert(intersection.isEmpty()) {
    """|Shadowed imports found in referenced.
       |Intersection:
       |${intersection.map { it.text }}
       |Shadowed:
       |${shadowedStatements.map { it.text }}
       |Referenced:
       |${referencedStatements.map { it.text }}""".trimMargin()
  }
}

private fun GroovyFileImports.findUnusedRegularImportStatements(file: GroovyFile): List<GrImportStatement> = findUnusedRegularImports(
  file).getStatements()

private fun GroovyFileImports.findUnusedStaticImportStatements(): List<GrImportStatement> = findUnusedStaticImports().getStatements()

private fun GroovyFileImports.findUnusedStarImportStatements(): List<GrImportStatement> = findUnusedStarImports().getStatements()

private fun GroovyFileImports.findUnusedRegularImports(file: GroovyFile): List<GroovyImport> {
  return regularImports.filter {
    val statement = it.statement
    statement != null && isUnused(file, statement, it)
  }
}

private fun GroovyFileImports.findUnusedStaticImports(): List<GroovyImport> {
  return staticImports.filter {
    it.statement != null && isUnused(it)
  }
}

private fun GroovyFileImports.findUnusedStarImports(): List<GroovyImport> {
  return starImports.filter {
    it.statement != null && isUnused(it)
  }
}

private fun GroovyFileImports.isUnused(file: GroovyFile, statement: GrImportStatement, import: RegularImport): Boolean {
  if (import.isAliased) return false

  val processor = ClassProcessor(import.name, file)
  val state = ResolveState.initial()

  for (_import in staticImports) {
    if (!_import.processDeclarations(processor, state, statement, file)) return false
  }

  val starImport = StarImport(getPackageName(import.classFqn))
  if (starImport.packageFqn == file.packageName) return true

  if (!file.processClassesInFile(processor, state)) return false
  if (!file.processClassesInPackage(processor, state)) return false

  if (starImport in starImports) return true

  for (_import in starImports) {
    if (!_import.processDeclarations(processor, state, statement, file)) return false
  }

  for (_import in staticStarImports) {
    if (!_import.processDeclarations(processor, state, statement, file)) return false
  }

  val results = processor.results
  assert(results.isEmpty()) {
    "Processor returned true, but there are ${results.size} results: $results"
  }

  return import in defaultRegularImportsSet || starImport in defaultStarImportsSet
}

private fun GroovyFileImports.isUnused(import: StaticImport): Boolean {
  if (import.isAliased) return false
  return StaticStarImport(import.classFqn) in staticStarImports
}

private fun isUnused(import: StarImport): Boolean {
  return import in defaultStarImportsSet
}

private fun Collection<GroovyImport>.getStatements(): List<GrImportStatement> {
  return mapNotNull {
    it.statement
  }
}

private fun GroovyFileImports.findUnresolvedNamedImports(file: GroovyFile, names: Collection<String>): Collection<GrImportStatement> {
  return allNamedImports.filter {
    it.statement != null && it.name in names && it.resolve(file) == null
  }.getStatements()
}

private fun GroovyFileImports.findUnresolvedStarImports(file: GroovyFile): List<GrImportStatement> {
  return starImports.filter {
    it.statement != null && it.resolve(file) == null
  }.getStatements()
}
