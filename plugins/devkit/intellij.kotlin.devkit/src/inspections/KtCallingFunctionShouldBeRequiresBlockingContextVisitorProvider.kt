// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.CallingMethodShouldBeRequiresBlockingContextInspection
import org.jetbrains.idea.devkit.util.QuickFixWithReferenceToElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KtCallingFunctionShouldBeRequiresBlockingContextVisitorProvider : CallingMethodShouldBeRequiresBlockingContextInspection.VisitorProvider {
  override fun provideVisitorForBody(method: PsiElement, holder: ProblemsHolder): PsiElementVisitor? {
    val ktFunction = method as? KtNamedFunction ?: return null
    return if (!ktFunction.hasModifier(KtTokens.SUSPEND_KEYWORD)) {
      FunctionBodyVisitor(holder, ktFunction)
    }
    else {
      null
    }
  }

  private class FunctionBodyVisitor(
    private val holder: ProblemsHolder,
    private val currentFunction: KtNamedFunction,
  ) : BlockingContextFunctionBodyVisitor() {
    override fun visitCallExpression(expression: KtCallExpression) {
      analyze(expression) {
        val functionCall = expression.resolveToCall()?.singleFunctionCallOrNull()
        val calledSymbol = functionCall?.partiallyAppliedSymbol?.symbol

        if (calledSymbol !is KaNamedSymbol) return
        val hasAnnotation = RequiresBlockingContextAnnotationId in calledSymbol.annotations

        if (!hasAnnotation) {
          if (calledSymbol is KaNamedFunctionSymbol && calledSymbol.isInline) {
            checkInlineLambdaArguments(functionCall)
          }

          return super.visitCallExpression(expression)
        }
      }

      holder.registerProblem(
        extractElementToHighlight(expression),
        DevKitBundle.message("inspections.calling.method.should.be.rbc.annotated.message"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        AnnotateFix(expression, currentFunction)
      )
    }
  }
}

private class AnnotateFix(
  element: PsiElement,
  callingMethod: KtNamedFunction,
) : QuickFixWithReferenceToElement<KtNamedFunction>(element, callingMethod) {

  override fun getFamilyName(): String = DevKitBundle.message("inspections.calling.method.should.be.rbc.annotated.annotate.fix")

  override fun getText(): String = familyName

  override fun invoke(
    project: Project,
    file: PsiFile,
    editor: Editor?,
    startElement: PsiElement,
    endElement: PsiElement,
  ) {
    referencedElement.element!!
      .addAnnotation(RequiresBlockingContextAnnotationId)
  }
}