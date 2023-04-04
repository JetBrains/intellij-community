// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.QuickFixWithReferenceToElement
import org.jetbrains.idea.devkit.util.REQUIRES_BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.idea.devkit.util.isInspectionForBlockingContextAvailable

class CallingJavaMethodShouldBerRequiresBlockingContextInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return if (isInspectionForBlockingContextAvailable(holder)) {
      MethodVisitor(holder)
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }
  }

  private class MethodVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      if (!AnnotationUtil.isAnnotated(method, REQUIRES_BLOCKING_CONTEXT_ANNOTATION, AnnotationUtil.CHECK_HIERARCHY)) {
        method.body?.accept(MethodBodyVisitor(holder, method))
      }
      super.visitMethod(method)
    }
  }

  private class MethodBodyVisitor(
    private val holder: ProblemsHolder,
    private val currentMethod: PsiMethod
  ) : JavaRecursiveElementWalkingVisitor() {
    override fun visitCallExpression(callExpression: PsiCallExpression) {
      val method = callExpression.resolveMethod()
      if (method == null || !AnnotationUtil.isAnnotated(method, REQUIRES_BLOCKING_CONTEXT_ANNOTATION, 0)) {
        return super.visitCallExpression(callExpression)
      }

      val elementToHighlight = when (callExpression) {
        is PsiMethodCallExpression -> callExpression.methodExpression.referenceNameElement
        is PsiNewExpression -> callExpression.classOrAnonymousClassReference?.referenceNameElement
        else -> null
      } ?: callExpression

      holder.registerProblem(
        elementToHighlight,
        DevKitBundle.message("inspections.calling.method.should.be.rbc.annotated.java.message"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        AnnotateFix(callExpression, currentMethod)
      )
    }

    override fun visitLambdaExpression(expression: PsiLambdaExpression): Unit = Unit

    override fun visitClass(aClass: PsiClass): Unit = Unit

    override fun visitMethod(method: PsiMethod): Unit = Unit
  }

  private class AnnotateFix(element: PsiElement, method: PsiMethod) : QuickFixWithReferenceToElement<PsiMethod>(element, method) {
    override fun getFamilyName(): String = DevKitBundle.message("inspections.calling.method.should.be.rbc.annotated.java.annotate.fix")
    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      AddAnnotationFix(REQUIRES_BLOCKING_CONTEXT_ANNOTATION, referencedElement.element!!).applyFix()
    }
  }
}