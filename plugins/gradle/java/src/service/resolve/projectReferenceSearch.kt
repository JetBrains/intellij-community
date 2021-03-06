// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

internal fun searchGradleProjectReferences(
  project: Project,
  projectSymbol: GradleProjectSymbol,
  searchScope: SearchScope
): Query<out GradleProjectReference> {
  val projectName = projectSymbol.projectName.takeIf(String::isNotEmpty) ?: return EmptyQuery.getEmptyQuery()
  return SearchService.getInstance()
    .searchWord(project, projectName)
    .inScope(searchScope)
    .inFilesWithLanguage(GroovyLanguage)
    .inContexts(SearchContext.IN_STRINGS)
    .buildQuery(LeafOccurrenceMapper.withPointer(projectSymbol.createPointer(), ::gradleProjectReferences))
}

private fun gradleProjectReferences(projectSymbol: GradleProjectSymbol, occurrence: LeafOccurrence): Collection<GradleProjectReference> {
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
