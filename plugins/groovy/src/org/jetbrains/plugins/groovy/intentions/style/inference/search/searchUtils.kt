// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.plugins.groovy.intentions.style.inference.search

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.Processor
import com.intellij.util.containers.map2Array
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod


internal fun searchWithClosureAvoidance(element: GrMethod, scope: SearchScope): List<PsiReference> {
  val requestsCollector = SearchRequestCollector(SearchSession(element))
  val collector: MutableList<PsiReference> = mutableListOf<PsiReference>()
  ReferencesSearch.searchOptimized(element, scope, false, requestsCollector) { ref -> collector.add(ref); true }
  val queries: MutableList<QuerySearchRequest> = requestsCollector.takeQueryRequests().toMutableList()
  while (queries.isNotEmpty()) {
    val query: QuerySearchRequest = queries.removeAt(queries.lastIndex)
    if (!query.runQuery()) break
    query.collector.takeQueryRequests().forEach { queries.add(it) }
    query.collector.takeSearchRequests().forEach { requestsCollector.adoptSearchRequest(it, element) }
  }
  PsiSearchHelper.getInstance(element.project).processRequests(requestsCollector) { ref -> collector.add(ref); true }
  return collector
}

private fun SearchRequestCollector.adoptSearchRequest(request: PsiSearchRequest, target: GrMethod) = with(request) {
  searchWord(word, searchScope, searchContext, caseSensitive, target, ScopeFilteringRequestProcessor(target, processor))
}

private class ScopeFilteringRequestProcessor(private val anchorElement: GrMethod,
                                             private val delegateProcessor: RequestResultProcessor) : RequestResultProcessor() {

  private class CollisionFinder(vararg val bannedIdentifiers: String) : GroovyRecursiveElementVisitor() {
    var foundCollision = false

    override fun visitElement(element: GroovyPsiElement) {
      if (!foundCollision) super.visitElement(element)
    }

    override fun visitReferenceExpression(referenceExpression: GrReferenceExpression) {
      if (referenceExpression.referenceName in bannedIdentifiers) {
        foundCollision = true
      }
      else {
        super.visitReferenceExpression(referenceExpression)
      }
    }

    override fun visitFunctionalExpression(expression: GrFunctionalExpression) {
      // it is sufficient for references searcher to know that argument has closure type, no need to go deeper
    }
  }


  override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
    val enclosingClosure: GrFunctionalExpression? = element.parentOfType<GrFunctionalExpression>()
    val call: GrMethodCall? = enclosingClosure?.parentOfType<GrMethodCall>()
    val arguments: List<GrExpression>? = call?.closureArguments?.asList()?.plus(call.expressionArguments.asList())
    if (arguments?.contains(enclosingClosure) == true && call.callReference?.methodName == anchorElement.name) {
      return true
    }
    if (checkSelfReferencesInArguments(element)) {
      return true
    }
    return delegateProcessor.processTextOccurrence(element, offsetInElement, consumer)
  }

  fun checkSelfReferencesInArguments(element: PsiElement): Boolean {
    val expressionWithArguments: GrExpression? = element.parentOfTypes(GrMethodCall::class, GrAssignmentExpression::class) ?: return false
    val isCorrectlyPointing = when (expressionWithArguments) {
      is GrMethodCall -> expressionWithArguments.invokedExpression === element
      is GrAssignmentExpression -> expressionWithArguments.lValue === element
      else -> return false
    }
    if (isCorrectlyPointing) {
      val enclosingMethod: GrMethod? = expressionWithArguments.parentOfType<GrMethod>()?.takeIf { it.name == anchorElement.name }
      val bannedIdentifiers: Array<String> = enclosingMethod?.parameters?.map2Array { it.name } ?: emptyArray()
      val collisionFinder = CollisionFinder(anchorElement.name, *bannedIdentifiers)
      val arguments = when (expressionWithArguments) {
        is GrMethodCall -> expressionWithArguments.expressionArguments
        is GrAssignmentExpression -> arrayOf(expressionWithArguments.rValue)
        else -> return false
      }
      if (arguments.any { argument -> collisionFinder.apply { argument.accept(this) }.foundCollision }) {
        // there should be provided some connection between method return type and its argument,
        // but currently this option is absent
        return true
      }
    }
    return false
  }

}