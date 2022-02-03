// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.Consumer
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyReferenceBase
import org.jetbrains.plugins.groovy.lang.resolve.doResolveStatic

class GrStaticExpressionReference(element: GrReferenceExpression) : GroovyReferenceBase<GrReferenceExpression>(element) {

  override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    require(!incomplete)
    // results of this reference doesn't depend on types and inference, and can be cached once and for all
    return TypeInferenceHelper.getTopContext().resolve(this, false, Resolver)
  }

  private object Resolver : DependentResolver<GrStaticExpressionReference>() {

    override fun doResolve(ref: GrStaticExpressionReference, incomplete: Boolean): Array<GroovyResolveResult> {
      return ref.element.doResolveStatic()?.let { it -> arrayOf(it) }?: GroovyResolveResult.EMPTY_ARRAY
    }

    override fun collectDependencies(ref: GrStaticExpressionReference, consumer: Consumer<in PsiPolyVariantReference>) {
      ref.element.qualifier?.accept(object : PsiRecursiveElementWalkingVisitor() {

        override fun visitElement(element: PsiElement) {
          when (element) {
            is GrReferenceExpression,
            is GrMethodCall,
            is GrParenthesizedExpression -> super.visitElement(element)
          }
        }

        override fun elementFinished(element: PsiElement) {
          if (element is GrReferenceExpression) {
            consumer.consume(element.staticReference)
          }
        }
      })
    }
  }
}
