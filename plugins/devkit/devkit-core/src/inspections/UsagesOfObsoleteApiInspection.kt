// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*

class UsagesOfObsoleteApiInspection : LocalInspectionTool() {

  private companion object {
    private val OBSOLETE_ANNOTATION_NAME = ApiStatus.Obsolete::class.java.canonicalName
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (JavaPsiFacade.getInstance(holder.project).findClass(OBSOLETE_ANNOTATION_NAME, holder.file.resolveScope) == null) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return ApiUsageUastVisitor.createPsiElementVisitor(
      ObsoleteApiUsageProcessor(holder)
    )
  }

  private class ObsoleteApiUsageProcessor(private val problemsHolder: ProblemsHolder) : ApiUsageProcessor {
    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      checkObsoleteApiUsage(target, sourceNode)
    }

    override fun processConstructorInvocation(
      sourceNode: UElement,
      instantiatedClass: PsiClass,
      constructor: PsiMethod?,
      subclassDeclaration: UClass?
    ) {
      if (constructor != null) {
        checkObsoleteApiUsage(constructor, sourceNode)
      }
    }

    override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
      checkObsoleteApiUsage(overriddenMethod, method)
    }

    private fun checkObsoleteApiUsage(target: PsiModifierListOwner, sourceNode: UElement) {
      val declaration = target.toUElement(UDeclaration::class.java)
      if (declaration != null && !arePsiElementsFromTheSameFile(sourceNode.sourcePsi, target)) {
        if (declaration !is UClass && declaration !is UMethod && declaration !is UField) {
          return
        }

        if (declaration.findAnnotation(OBSOLETE_ANNOTATION_NAME) != null) {
          val elementToHighlight = (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi ?: return
          problemsHolder.registerProblem(elementToHighlight, DevKitBundle.message("inspection.message.obsolete.api.used"))
        }
      }
    }

    private fun arePsiElementsFromTheSameFile(one: PsiElement?, two: PsiElement?): Boolean {
      //For Kotlin: naive comparison of PSI containingFile-s does not work because one of the PSI elements might be light PSI element
      // coming from a light PSI file, and another element would be physical PSI file, and they are not "equals()".
      return one?.containingFile?.virtualFile == two?.containingFile?.virtualFile
    }
  }
}

