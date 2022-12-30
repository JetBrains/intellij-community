// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.highlighting

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.types.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GinqHighlightingVisitor : GroovyRecursiveElementVisitor() {
  val keywords: MutableList<PsiElement> = mutableListOf()
  val softKeywords: MutableList<PsiElement> = mutableListOf()
  val warnings: MutableList<ParsingError> = mutableListOf()

  override fun visitElement(element: GroovyPsiElement) {
    val ginq = element.getStoredGinq()
    if (ginq != null) {
      keywords.addAll(ginq.getQueryFragments().map { it.keyword })
      keywords.addAll(ginq.select?.projections?.flatMap { projection ->
        projection.windows.flatMap { listOfNotNull(it.overKw, it.rowsOrRangeKw, it.partitionKw, it.orderBy?.keyword) }
      } ?: emptyList())
      warnings.addAll(getTypecheckingWarnings(ginq))
      val orderBys = (ginq.select?.projections?.flatMap { it.windows.mapNotNull(GinqWindowFragment::orderBy) } ?: emptyList()) + listOfNotNull(ginq.orderBy)
      softKeywords.addAll(orderBys.flatMap { it.getSoftKeywords() })
    }
    super.visitElement(element)
  }

  private fun GinqOrderByFragment.getSoftKeywords() : List<PsiElement> {
    return sortingFields.mapNotNull { it.orderKw } + sortingFields.mapNotNull { it.nullsKw }
  }

  private fun getTypecheckingWarnings(ginq: GinqExpression): Collection<ParsingError> {
    val dataSourceFragments = ginq.getDataSourceFragments()
    val filteringFragments = ginq.getFilterFragments()
    val filterResults = filteringFragments.mapNotNull { fragment ->
      val type = fragment.filter.type
      val parentCall = fragment.filter.parentOfType<GrMethodCall>()?.parentOfType<GrMethodCall>()?.invokedExpression?.asSafely<GrReferenceExpression>()?.takeIf { it.referenceName == "exists" }
      if (type != PsiType.BOOLEAN && type?.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) != true && parentCall == null) {
        fragment.filter to GroovyBundle.message("ginq.error.message.boolean.condition.expected")
      }
      else {
        null
      }
    }
    val dataSourceResults = dataSourceFragments.mapNotNull {
      val type = inferDataSourceComponentType(it.dataSource.type)
      if (type == null) {
        it.dataSource to GroovyBundle.message("ginq.error.message.container.expected")
      }
      else {
        null
      }
    }
    return filterResults + dataSourceResults
  }
}