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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
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

  private class CollisionFinder(val bannedIdentifiers: List<String>,
                                val bannedElements: List<PsiElement>) : GroovyRecursiveElementVisitor() {
    var foundCollision = false

    override fun visitElement(element: GroovyPsiElement) {
      if (!foundCollision) super.visitElement(element)
    }

    override fun visitReferenceExpression(referenceExpression: GrReferenceExpression) {
      if (referenceExpression.referenceName in bannedIdentifiers ||
          referenceExpression.staticReference.resolve() in bannedElements) {
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
    if (element.findElementAt(offsetInElement)?.parentOfType<GrLiteral>() != null) {
      return true
    }
    val enclosingMethod: GrMethod? = element.parentOfType<GrMethod>()
    val bannedElements: List<PsiElement> = enclosingMethod?.parameters?.filter { it.typeElement == null } ?: emptyList()
    val collisionFinder = CollisionFinder(listOf(anchorElement.name), bannedElements)
    if (hasSelfReferencesInCaller(element, collisionFinder) || hasSelfReferencesInArguments(element, collisionFinder)) {
      return true
    }
    return delegateProcessor.processTextOccurrence(element, offsetInElement, consumer)
  }

  fun hasSelfReferencesInCaller(element: PsiElement, collisionFinder: CollisionFinder): Boolean {
    val enclosingClosure: GrFunctionalExpression = element.parentOfType<GrFunctionalExpression>() ?: return false
    val call: GrMethodCall = enclosingClosure.parentOfType<GrMethodCall>() ?: return false
    val arguments: List<GrExpression> = call.closureArguments?.asList()?.plus(call.expressionArguments.asList()) ?: emptyList()
    if (enclosingClosure !in arguments) {
      return false
    }
    if (collisionFinder.apply(call::accept).foundCollision) {
      return true
    }
    return hasSelfReferencesInCaller(call, collisionFinder)
  }

  fun hasSelfReferencesInArguments(element: PsiElement, collisionFinder: CollisionFinder): Boolean {
    val expressionWithArguments: GrExpression? = element.parentOfTypes(GrMethodCall::class, GrAssignmentExpression::class) ?: return false
    val isCorrectlyPointing = when (expressionWithArguments) {
      is GrMethodCall -> expressionWithArguments.invokedExpression === element
      is GrAssignmentExpression -> expressionWithArguments.lValue === element
      else -> return false
    }
    if (isCorrectlyPointing) {
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