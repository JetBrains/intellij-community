package com.intellij.completion.ml.local.models.frequency

import com.intellij.completion.ml.local.models.api.LocalModel
import com.intellij.completion.ml.local.models.storage.MethodsFrequencyStorage
import com.intellij.completion.ml.local.util.LocalModelsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*

class MethodsFrequencyLocalModel private constructor(private val storage: MethodsFrequencyStorage) : LocalModel {
  companion object {
    fun create(project: Project): MethodsFrequencyLocalModel {
      val storagesPath = LocalModelsUtil.storagePath(project)
      val methodsFrequencyStorage = MethodsFrequencyStorage.getStorage(storagesPath)
      return MethodsFrequencyLocalModel(methodsFrequencyStorage)
    }
  }

  fun totalMethodsCount(): Int = storage.totalMethods

  fun totalMethodsUsages(): Int = storage.totalMethodsUsages

  fun getMethodsByClass(className: String): MethodsFrequencies? = storage.get(className)

  override fun fileVisitor(): PsiElementVisitor = object : JavaRecursiveElementWalkingVisitor() {

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      expression.resolveMethod()?.let { method ->
        LocalModelsUtil.getMethodName(method)?.let { methodName ->
          LocalModelsUtil.getClassName(method.containingClass)?.let { clsName ->
            storage.addMethodUsage(clsName, methodName)
          }
        }
      }
      super.visitMethodCallExpression(expression)
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

  override fun readyToUse(): Boolean = storage.isValid() && !storage.isEmpty()
}