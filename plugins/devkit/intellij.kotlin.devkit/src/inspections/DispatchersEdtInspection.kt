// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.ImportPath

class DispatchersEdtInspection : LocalInspectionTool() {
  companion object {
    private const val UI = "UI"
    private const val EDT = "EDT"
    private const val GET_EDT = "getEDT"
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return if (DevKitInspectionUtil.isAllowedIncludingTestSources(holder.file)) {
      createFileVisitor(holder)
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }
  }

  private fun createFileVisitor(holder: ProblemsHolder): PsiElementVisitor {
    val dispatcherEdtFinderVIsitor = lazy(mode = LazyThreadSafetyMode.NONE) {
      DispatcherEdtFinderVisitor(holder)
    }

    return TopLevelFunctionVisitor(dispatcherEdtFinderVIsitor)
  }

  private class DispatcherEdtFinderVisitor(
    private val holder: ProblemsHolder,
  ) : KtTreeVisitorVoid() {

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) = Unit

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
      if (expression !is KtNameReferenceExpression) return
      analyze(expression) {
        val name = expression.getReferencedName()
        if (name != EDT && name != GET_EDT) {
          return@analyze
        }
        val resolveResult = expression.references.firstNotNullOfOrNull { it.resolve() }
        if (resolveResult !is KtProperty || resolveResult.fqName != FqName(INTELLIJ_EDT_DISPATCHER)) {
          return@analyze
        }
        holder.registerProblem(
          expression,
          DevKitKotlinBundle.message("inspection.dispatchers.edt.text"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          ReplaceWithDispatchersUiQuickFix(expression)
        )
      }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
      visitElement(expression)
    }

    private class ReplaceWithDispatchersUiQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
      override fun getFamilyName(): String {
        return DevKitKotlinBundle.message("inspection.dispatchers.edt.to.dispatchers.ui.fix.text")
      }

      override fun getText(): @IntentionName String {
        return familyName
      }


      override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
        return true
      }

      override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        require(file is KtFile)
        val factory = KtPsiFactory(project)
        val importDirective = factory.createImportDirective(ImportPath(FqName(INTELLIJ_UI_DISPATCHER), false))
        val ktFile = file
        ktFile.importList?.add(importDirective)
        val resultExpression = startElement.replace(factory.createExpression(UI))
        ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
      }
    }
  }
}
