// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression

class GinqWindowCollector : GroovyRecursiveElementVisitor() {
  private val myWindows: MutableList<GinqWindowFragment> = mutableListOf()

  val windows: List<GinqWindowFragment> get() = myWindows

  override fun visitMethodCallExpression(methodCallExpression: GrMethodCallExpression) {
    val invoked = methodCallExpression.invokedExpression.asSafely<GrReferenceExpression>()?.takeIf { it.referenceName == "over" } ?: return super.visitMethodCallExpression(methodCallExpression)
    val qualifier = invoked.qualifierExpression ?: return super.visitMethodCallExpression(methodCallExpression)
    val argument = methodCallExpression.argumentList.allArguments.takeIf { it.size <= 1 } ?: return super.visitMethodCallExpression(methodCallExpression)
    val overKw = invoked.referenceNameElement ?: return super.visitMethodCallExpression(methodCallExpression)
    if (argument.isEmpty()) {
      myWindows.add(GinqWindowFragment(qualifier, overKw, null, emptyList(), null, null, emptyList()))
    } else {
      var partitionKw: PsiElement? = null
      var partitionArguments: List<GrExpression> = emptyList()
      var orderBy: GinqOrderByFragment? = null
      var rowsOrRangeKw: PsiElement? = null
      var rowsOrRangeArguments: List<GrExpression> = emptyList()
      var localQualifier = argument.single()
      while (localQualifier != null) {
        val call = localQualifier.asSafely<GrMethodCall>()
        if (call == null) {
          if (localQualifier is GrReferenceExpression) {
            localQualifier = localQualifier.qualifierExpression
            continue
          } else {
            break
          }
        }
        val invokedInner = call.invokedExpression.asSafely<GrReferenceExpression>() ?: break
        when (invokedInner.referenceName) {
          "range", "rows" -> {
            rowsOrRangeKw = invokedInner.referenceNameElement
            rowsOrRangeArguments = call.argumentList.allArguments.toList().filterIsInstance<GrExpression>()
            rowsOrRangeArguments.forEach { it.markAsGinqUntransformed() }
            localQualifier = invokedInner.qualifier
          }
          "partitionby" -> {
            partitionKw = invokedInner.referenceNameElement
            partitionArguments = call.argumentList.allArguments.toList().filterIsInstance<GrExpression>()
            partitionArguments.forEach { it.markAsGinqUntransformed() }
            localQualifier = invokedInner.qualifier
          }
          "orderby" -> {
            val orderByKw = invokedInner.referenceNameElement!!
            val fields = call.argumentList.allArguments.toList().mapNotNull { it.asSafely<GrExpression>()?.let(::getOrdering) }
            orderBy = GinqOrderByFragment(orderByKw, fields)
            orderBy.sortingFields.forEach { it.sorter.markAsGinqUntransformed() }
            localQualifier = invokedInner.qualifier
          }
          else -> break
        }
      }
      myWindows.add(GinqWindowFragment(qualifier, overKw, partitionKw, partitionArguments, orderBy, rowsOrRangeKw, rowsOrRangeArguments))
    }
    qualifier.markAsGinqUntransformed()
    super.visitMethodCallExpression(methodCallExpression)
  }
}


