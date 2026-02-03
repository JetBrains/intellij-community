// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.toml

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.toml.getVersionCatalogParts
import org.jetbrains.plugins.groovy.findUsages.GroovyScopeUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue

class GradleGroovyVersionCatalogReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(false) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val element = queryParameters.elementToSearch
    if (element !is TomlKeySegment) {
      return
    }

    val (keyValue, name) = runReadAction {
      element.parentOfType<TomlKeyValue>() to element.name
    }
    keyValue ?: return
    val nameParts = name?.getVersionCatalogParts() ?: return
    val identifier = nameParts.lastOrNull() ?: return
    val getter = PropertyUtilBase.getAccessorName(identifier, PropertyKind.GETTER)

    val searchScope = GroovyScopeUtil.restrictScopeToGroovyFiles(queryParameters.effectiveSearchScope)
    val searchContext = UsageSearchContext.IN_CODE
    val processor = MyProcessor(keyValue, nameParts)

    val search : (String) -> Unit = { queryParameters.optimizer.searchWord(it, searchScope, searchContext, true, element, processor) }
    search(identifier)
    search(getter)
  }

  class MyProcessor(private val searchedElement: TomlKeyValue, private val oldNameParts: List<String>) : RequestResultProcessor() {

    override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
      val parent = element.parentOfType<GrReferenceExpression>() ?: return true
      val handler = GradleVersionCatalogTomlAwareGotoDeclarationHandler()
      val gotoDeclarationTargets = handler.getGotoDeclarationTargets(element, 0, null)
      if (gotoDeclarationTargets?.singleOrNull() == searchedElement) {
        return consumer.process(GroovyVersionCatalogReference(parent, oldNameParts, searchedElement))
      }
      return true
    }
  }

  private class GroovyVersionCatalogReference(refExpr : GrReferenceExpression, val oldNameParts: List<String>, val searchedElement: TomlKeyValue)
    : PsiReferenceBase<GrReferenceExpression>(refExpr) {
    override fun resolve(): PsiElement {
      return searchedElement
    }

    override fun handleElementRename(newElementName: String): PsiElement {
      val parts = newElementName.getVersionCatalogParts()
      var referencingElement = element
      repeat(oldNameParts.size) {
        when (val qualifier = referencingElement.qualifierExpression) {
          is GrMethodCall -> referencingElement = qualifier.invokedExpression.asSafely<GrReferenceExpression>() ?: return element
          is GrReferenceExpression -> referencingElement = qualifier
          else -> return element
        }
      }
      val elementToReplace = getActualReferencingElement(element)
      val rootElement = getActualReferencingElement(referencingElement)
      var newElementText = rootElement.text
      for (newPart in parts) {
        newElementText = "$newElementText.$newPart"
      }
      val newElement = GroovyPsiElementFactory.getInstance(element.project).createExpressionFromText(newElementText)
      return elementToReplace.replace(newElement)
    }

    private fun getActualReferencingElement(elem: PsiElement): PsiElement {
      return if (elem.parent is GrMethodCall) {
        elem.parent
      } else {
        elem
      }
    }
  }
}