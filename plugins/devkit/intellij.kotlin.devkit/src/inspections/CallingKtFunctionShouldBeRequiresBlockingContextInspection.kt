// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.QuickFixWithReferenceToElement
import org.jetbrains.idea.devkit.util.isInspectionForBlockingContextAvailable
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class CallingKtFunctionShouldBeRequiresBlockingContextInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return if (isInspectionForBlockingContextAvailable(holder)) {
      FunctionVisitor(holder)
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }
  }

  private class FunctionVisitor(
    private val holder: ProblemsHolder,
  ) : KtTreeVisitorVoid() {
    override fun visitElement(element: PsiElement) = Unit

    override fun visitNamedFunction(function: KtNamedFunction) {
      if (!function.hasModifier(KtTokens.SUSPEND_KEYWORD) && !isAnnotated(function)) {
        function.bodyExpression?.accept(FunctionBodyVisitor(holder, function))
        return
      }
      super.visitNamedFunction(function)
    }
  }

  private class FunctionBodyVisitor(
    private val holder: ProblemsHolder,
    private val currentFunction: KtNamedFunction,
  ) : BlockingContextFunctionBodyVisitor() {
    override fun visitCallExpression(expression: KtCallExpression) {
      analyze(expression) {
        val functionCall = expression.resolveCall().singleFunctionCallOrNull()
        val calledSymbol = functionCall?.partiallyAppliedSymbol?.symbol

        if (calledSymbol !is KtNamedSymbol) return
        val hasAnnotation = calledSymbol.hasAnnotation(requiresBlockingContextAnnotationId)

        if (!hasAnnotation) {
          if (calledSymbol is KtFunctionSymbol && calledSymbol.isInline) {
            checkInlineLambdaArguments(functionCall)
          }

          return super.visitCallExpression(expression)
        }
      }

      holder.registerProblem(
        extractElementToHighlight(expression),
        DevKitKotlinBundle.message("inspections.calling.method.should.be.rbc.annotated.kt.message"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        AnnotateFix(expression, currentFunction)
      )
    }
  }
}

private class AnnotateFix(
  element: PsiElement, callingMethod: KtNamedFunction
) : QuickFixWithReferenceToElement<KtNamedFunction>(element, callingMethod) {
  override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.calling.method.should.be.rbc.annotated.kt.annotate.fix")

  override fun getText(): String = familyName

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    referencedElement.element!!.addAnnotation(requiresBlockingContextAnnotation)
  }
}

private fun isAnnotated(function: KtNamedFunction): Boolean {
  return analyze(function) {
    val functionSymbol = function.getFunctionLikeSymbol()

    functionSymbol.hasAnnotation(requiresBlockingContextAnnotationId) ||
    functionSymbol.getAllOverriddenSymbols().any { it.hasAnnotation(requiresBlockingContextAnnotationId) }
  }
}