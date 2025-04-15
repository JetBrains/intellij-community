// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageExtension
import com.intellij.modcommand.Presentation
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.*
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.REQUIRES_BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.idea.devkit.util.isInspectionForBlockingContextAvailable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

@VisibleForTesting
@IntellijInternalApi
@Internal
class CallingMethodShouldBeRequiresBlockingContextInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return if (isInspectionForBlockingContextAvailable(holder)) {
      UastHintedVisitorAdapter.create(holder.file.language, MethodVisitor(holder), arrayOf(UMethod::class.java))
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }
  }

  private class MethodVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitMethod(node: UMethod): Boolean {
      val psiElementForMethod = node.sourcePsi ?: return true
      val bodyPsi = node.uastBody?.sourcePsi ?: return true

      if (!AnnotationUtil.isAnnotated(node.javaPsi, REQUIRES_BLOCKING_CONTEXT_ANNOTATION, AnnotationUtil.CHECK_HIERARCHY)) {
        val visitor = VisitorProviders.forLanguage(psiElementForMethod.language)?.provideVisitorForBody(psiElementForMethod, holder)
                      ?: return true
        bodyPsi.accept(visitor)
      }

      return true
    }
  }

  @IntellijInternalApi
  @Internal
  interface VisitorProvider {
    fun provideVisitorForBody(method: PsiElement, holder: ProblemsHolder): PsiElementVisitor?
  }

  internal class VisitorProviderForJava : VisitorProvider {
    override fun provideVisitorForBody(method: PsiElement, holder: ProblemsHolder): PsiElementVisitor? {
      val javaMethod = method as? PsiMethod ?: return null
      return MethodBodyVisitor(holder, javaMethod)
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

      holder.problem(
        elementToHighlight,
        DevKitBundle.message("inspections.calling.method.should.be.rbc.annotated.message"))
        .highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        .fix(AddAnnotationModCommandAction(REQUIRES_BLOCKING_CONTEXT_ANNOTATION, currentMethod)
               .withPresentation { p -> Presentation.of(DevKitBundle.message("inspections.calling.method.should.be.rbc.annotated.annotate.fix")) }
      ).register()
    }

    override fun visitLambdaExpression(expression: PsiLambdaExpression): Unit = Unit

    override fun visitClass(aClass: PsiClass): Unit = Unit

    override fun visitMethod(method: PsiMethod): Unit = Unit
  }
}

private val EP_NAME = ExtensionPointName.create<CallingMethodShouldBeRequiresBlockingContextInspection.VisitorProvider>(
  "DevKit.lang.visitorProviderForRBCInspection"
)
private object VisitorProviders : LanguageExtension<CallingMethodShouldBeRequiresBlockingContextInspection.VisitorProvider>(EP_NAME.name)