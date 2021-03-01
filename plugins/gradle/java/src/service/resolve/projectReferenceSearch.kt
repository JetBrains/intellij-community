// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.search.*
import com.intellij.psi.util.walkUp
import com.intellij.util.Query
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

class GradleProjectReferenceSearcher : PsiSymbolReferenceSearcher {

  override fun collectSearchRequests(parameters: PsiSymbolReferenceSearchParameters): Collection<Query<out PsiSymbolReference>> {
    val projectSymbol = parameters.symbol as? GradleProjectSymbol ?: return emptyList()
    val projectName = projectSymbol.projectName.takeIf(String::isNotEmpty) ?: return emptyList()
    val query = SearchService.getInstance()
      .searchWord(parameters.project, projectName)
      .inScope(parameters.searchScope)
      .inFilesWithLanguage(GroovyLanguage)
      .inContexts(SearchContext.IN_STRINGS)
      .buildQuery(LeafOccurrenceMapper.withPointer(projectSymbol.createPointer(), ::gradleProjectReferences))
    return listOf(query)
  }

  private fun gradleProjectReferences(projectSymbol: GradleProjectSymbol, occurrence: LeafOccurrence): Collection<PsiSymbolReference> {
    val (scope, start, offsetInStart) = occurrence
    for ((element, offsetInElement) in walkUp(start, offsetInStart, scope)) {
      if (element is GrLiteral) {
        return getReferences(element)
          .filter {
            it.rangeInElement.containsOffset(offsetInElement)
          }
          .filter {
            it.resolvesTo(projectSymbol)
          }
      }
    }
    return emptyList()
  }
}
