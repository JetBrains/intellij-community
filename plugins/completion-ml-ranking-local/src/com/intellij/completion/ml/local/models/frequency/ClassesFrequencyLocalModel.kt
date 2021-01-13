package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.storage.ClassesFrequencyStorage
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil

class ClassesFrequencyLocalModel private constructor(private val storage: ClassesFrequencyStorage) : LocalModel {
  companion object {
    fun create(project: Project): ClassesFrequencyLocalModel {
      val storagesPath = LocalModelsUtil.storagePath(project)
      val storage = ClassesFrequencyStorage.getStorage(storagesPath)
      return ClassesFrequencyLocalModel(storage)
    }
  }

  fun totalClassesCount(): Int = storage.totalClasses

  fun totalClassesUsages(): Int = storage.totalClassesUsages

  fun getClass(className: String): Int? = storage.get(className)

  override fun fileVisitor(): PsiElementVisitor = object : JavaRecursiveElementWalkingVisitor() {

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
        storage.addClassUsage(it)
      }
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) = Unit
    override fun visitImportStatement(statement: PsiImportStatement) = Unit
    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) = Unit
  }

  override fun onStarted() {
    storage.setValid(false)
  }

  override fun onFinished() {
    storage.setValid(true)
  }
}