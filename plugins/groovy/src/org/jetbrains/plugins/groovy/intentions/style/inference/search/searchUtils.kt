// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.search

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

@Suppress("RemoveExplicitTypeArguments")
internal fun searchWithClosureAvoidance(element: GrMethod, scope: SearchScope): List<PsiReference> {
  val requestsCollector = SearchRequestCollector(SearchSession())
  val collector: MutableList<PsiReference> = mutableListOf<PsiReference>()
  ReferencesSearch.searchOptimized(element, scope, false, requestsCollector) { ref -> collector.add(ref); true }
  val queries: MutableList<QuerySearchRequest> = requestsCollector.takeQueryRequests().toMutableList()
  while (queries.isNotEmpty()) {
    val query: QuerySearchRequest = queries.removeAt(queries.lastIndex)
    if (!query.runQuery()) break
    query.collector.takeQueryRequests().forEach { queries.add(it) }
    query.collector.takeSearchRequests().forEach { dumpSearchRequest(requestsCollector, it, element) }
  }
  PsiSearchHelper.getInstance(element.project).processRequests(requestsCollector) { ref -> collector.add(ref); true }
  return collector
}

private fun dumpSearchRequest(requestsCollector: SearchRequestCollector, request: PsiSearchRequest, target: GrMethod) = with(request) {
  requestsCollector.searchWord(word, searchScope, searchContext, caseSensitive, target, ScopeFilteringRequestProcessor(target, processor))
}

private class ScopeFilteringRequestProcessor(private val anchorElement: GrMethod,
                                             private val delegateProcessor: RequestResultProcessor) : RequestResultProcessor() {
  override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
    val enclosingClosure = element.parentOfType<GrFunctionalExpression>()
    val call = enclosingClosure?.parentOfType<GrMethodCall>()
    val arguments = call?.closureArguments?.asList()?.plus(call.expressionArguments.asList())
    if (arguments?.contains(enclosingClosure) == true && call.callReference?.methodName == anchorElement.name) {
      return true
    }
    else {
      return delegateProcessor.processTextOccurrence(element, offsetInElement, consumer)
    }
  }

}