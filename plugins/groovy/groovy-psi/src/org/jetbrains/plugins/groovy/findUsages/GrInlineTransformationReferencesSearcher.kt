// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Processor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.transformations.inline.getHierarchicalInlineTransformationData

class GrInlineTransformationReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val target = queryParameters.elementToSearch
    runReadAction {
      if (target.containingFile !is GroovyFileBase) {
        return@runReadAction
      }
      val (call, performer) = getHierarchicalInlineTransformationData(target) ?: return@runReadAction
      call.accept(object : GroovyRecursiveElementVisitor() {
        override fun visitElement(element: GroovyPsiElement) {
          val reference = performer.computeStaticReference(element) ?: return super.visitElement(element)
          if (reference.element != target) {
            return super.visitElement(element)
          }
          val ref = object : PsiReferenceBase<PsiElement>(element, TextRange(0, element.endOffset - element.startOffset), true) {
            override fun resolve(): PsiElement = target
          }
          consumer.process(ref)
        }
      })
    }
  }
}