/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("GroovyUnusedImportUtil")

package org.jetbrains.plugins.groovy.lang.resolve.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.containers.HashSet
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition

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

  val usedStatements = HashSet(referencedStatements)
  usedStatements -= imports.findUnnecessaryStatements()
  usedStatements += imports.findUnresolvedStatements(unresolvedReferenceNames)
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
      results.mapNotNullTo(referencedStatements) {
        it.currentFileResolveContext as? GrImportStatement
      }
    }
  }
}
