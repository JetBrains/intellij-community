// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.slicer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceUsage
import com.intellij.util.Processor
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovySliceUsage : SliceUsage {
  constructor(element: PsiElement, parent: SliceUsage) : super(element, parent)
  constructor(element: PsiElement, params: SliceAnalysisParams) : super(element, params)

  override fun copy(): GroovySliceUsage {
    val element = usageInfo.element!!
    if (parent == null) return GroovySliceUsage(element, params)
    return GroovySliceUsage(element, parent)
  }

  private fun PsiElement.passToProcessor(processor: Processor<in SliceUsage>) {
    processor.process(createUsage(this@GroovySliceUsage))
  }

  // TODO: This is a simple implementation created mainly for the purpose of cross-language graph testing

  companion object {
    private fun PsiElement.createUsage(parent: SliceUsage) = GroovySliceUsage(this, parent)

    private fun PsiElement.passToProcessor(parent: SliceUsage, processor: Processor<in SliceUsage>) {
      processor.process(createUsage(parent))
    }

    fun processMethodReturnValues(method: GrMethod, parent: SliceUsage, processor: Processor<in SliceUsage>) {
      method.block?.accept(
          object : GroovyRecursiveElementVisitor() {
            override fun visitReturnStatement(returnStatement: GrReturnStatement) {
              returnStatement.returnValue?.passToProcessor(parent, processor)
            }
          }
      )
    }
  }

  public override fun processUsagesFlownDownTo(element: PsiElement, processor: Processor<in SliceUsage>) {
    when (element) {
      is GrVariable -> element.initializerGroovy?.passToProcessor(processor)
      is GrReferenceExpression -> (element.resolve() as? PsiVariable)?.passToProcessor(processor)
      is GrMethodCall -> {
        val method = element.resolveMethod() ?: return

        if (method !is GrMethod) {
          return method.passToProcessor(processor)
        }

        processMethodReturnValues(method, this, processor)

        method.block?.accept(
            object : GroovyRecursiveElementVisitor() {
              override fun visitReturnStatement(returnStatement: GrReturnStatement) {
                returnStatement.returnValue?.passToProcessor(processor)
              }
            }
        )
      }
    }
  }

  public override fun processUsagesFlownFromThe(element: PsiElement, processor: Processor<in SliceUsage>) {
    when (element) {
      is GrVariable -> {
        ReferencesSearch.search(element, params.scope.toSearchScope()).asIterable().forEach {
          val refElement = it.element
          if (refElement.language != GroovyLanguage) return refElement.passToProcessor(processor)

          processUsagesFlownFromThe(refElement, processor)
        }
      }

      is GrExpression -> {
        val parent = element.parent
        if (parent is GrVariable && parent.initializerGroovy == element) {
          parent.passToProcessor(processor)
        }
        if (parent is GrAssignmentExpression && parent.rValue == element) {
          (parent.lValue as? GrReferenceExpression)?.resolve()?.passToProcessor(processor)
        }
        else if (parent is GrReturnStatement) {
          val method = PsiTreeUtil.getParentOfType(parent, GrMethod::class.java) ?: return
          MethodReferencesSearch.search(method, params.scope.toSearchScope(), true).asIterable().forEach {
            val callElement = it.element.parent
            if (callElement.language != GroovyLanguage) return callElement.passToProcessor(processor)

            (callElement as? GrCallExpression)?.passToProcessor(processor)
          }
        }
      }
    }
  }
}