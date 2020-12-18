package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil

class ReferenceFrequencyVisitor(private val usagesTracker: UsagesTracker) : JavaRecursiveElementWalkingVisitor() {

  override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
    expression.resolveMethod()?.let { method ->
      LocalModelsUtil.getMethodName(method)?.let { methodName ->
        LocalModelsUtil.getClassName(method.containingClass)?.let { clsName ->
          usagesTracker.methodUsed(clsName, methodName)
        }
      }
    }
    super.visitMethodCallExpression(expression)
  }

  override fun visitNewExpression(expression: PsiNewExpression) {
    val cls = expression.classReference?.resolve()
    if (cls is PsiClass) {
      addClassUsage(cls)
    }
    super.visitNewExpression(expression)
  }

  override fun visitClassObjectAccessExpression(expression: PsiClassObjectAccessExpression) {
    PsiTypesUtil.getPsiClass(expression.operand.type)?.let { cls ->
      addClassUsage(cls)
    }
  }

  override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
    val castType = expression.castType
    if (castType != null) {
      PsiTypesUtil.getPsiClass(castType.type)?.let { cls ->
        addClassUsage(cls)
      }
    }
    expression.operand?.accept(this)
  }

  override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
    expression.operand.accept(this)
    val checkType = expression.checkType ?: return
    PsiTypesUtil.getPsiClass(checkType.type)?.let { cls ->
      addClassUsage(cls)
    }
  }

  override fun visitReferenceExpression(expression: PsiReferenceExpression) {
    expression.qualifierExpression?.let { qualifier ->
      if (qualifier is PsiReferenceExpression) {
        qualifier.resolve()?.let { def ->
          if (def is PsiClass) {
            addClassUsage(def)
          }
        }
      }
    }
    super.visitReferenceExpression(expression)
  }

  private fun addClassUsage(cls: PsiClass) {
    LocalModelsUtil.getClassName(cls)?.let {
      usagesTracker.classUsed(it)
    }
  }

  override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) = Unit
  override fun visitImportStatement(statement: PsiImportStatement) = Unit
  override fun visitImportStaticStatement(statement: PsiImportStaticStatement) = Unit
}