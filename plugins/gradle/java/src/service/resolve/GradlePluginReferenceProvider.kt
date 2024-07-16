// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyMethodCallPattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyElement
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod

/**
 * Provides a reference for plugin ID - a string literal, coming after `id` in the `plugins` closure (e.g., `plugins { id 'my-plugin-id' }`)
 */
@Internal
class GradlePluginReferenceProvider : PsiSymbolReferenceProvider {

  /**
   * TODO maybe implement a handler like [GradleProjectUsageSearcher].
   */
  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    return if (element is GrLiteral) getPluginReferences(element) else emptyList()
  }
}

private val myPattern = GroovyPatterns.stringLiteral().withParent(
  groovyElement<GrArgumentList>().withParent(
    GroovyMethodCallPattern.resolvesTo(psiMethod(GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCIES_SPEC, "id"))
  )
)

internal fun getPluginReferences(element: GrLiteral): List<GradlePluginReference> {
  if (!myPattern.accepts(element)) {
    return emptyList()
  }
  val escaper = element.createLiteralTextEscaper()
  val manipulator = ElementManipulators.getManipulator(element)
  val value = StringBuilder()
  val rangeInHost = manipulator.getRangeInElement(element)
  if (!escaper.decode(rangeInHost, value)) {
    return emptyList()
  }
  return listOf(GradlePluginReference(element, rangeInHost, value.toString()))
}
