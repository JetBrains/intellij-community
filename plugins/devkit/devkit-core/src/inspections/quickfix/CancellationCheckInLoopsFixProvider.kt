// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInsight.BlockUtils
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.CommonJavaRefactoringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle


private val EP_NAME: ExtensionPointName<CancellationCheckInLoopsFixProvider> = ExtensionPointName.create(
  "DevKit.lang.cancellationCheckInLoopsFixProvider")

internal object CancellationCheckInLoopsFixProviders : LanguageExtension<CancellationCheckInLoopsFixProvider>(EP_NAME.name)


@IntellijInternalApi
@ApiStatus.Internal
interface CancellationCheckInLoopsFixProvider {

  fun getFixes(anchor: PsiElement, cancellationCheckFqn: String): List<LocalQuickFix>

}


internal class JavaCancellationCheckInLoopsFixProvider : CancellationCheckInLoopsFixProvider {

  override fun getFixes(anchor: PsiElement, cancellationCheckFqn: String): List<LocalQuickFix> {
    return when (anchor) {
      is PsiIdentifier -> listOf(InsertCancellationCheckInLoopMethodFix(cancellationCheckFqn, anchor))
      else -> listOf(InsertCancellationCheckInLoopFix(cancellationCheckFqn, anchor))
    }
  }
}

internal class InsertCancellationCheckInLoopFix(cancellationCheckCallFqn: String, loopKeyword: PsiElement)
  : AbstractInsertCancellationCheckInLoopFix(cancellationCheckCallFqn, loopKeyword) {

  override fun isAvailable(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return startElement.parentOfType<PsiLoopStatement>() != null
  }

  override fun getCodeBlock(startElement: PsiElement): PsiCodeBlock? {
    val loopStatement = startElement.parentOfType<PsiLoopStatement>() ?: return null
    val body = loopStatement.body ?: return null
    return BlockUtils.expandSingleStatementToBlockStatement(body).parentOfType<PsiBlockStatement>(withSelf = true)?.codeBlock
  }
}

internal class InsertCancellationCheckInLoopMethodFix(cancellationCheckCallFqn: String, methodIdentifier: PsiElement)
  : AbstractInsertCancellationCheckInLoopFix(cancellationCheckCallFqn, methodIdentifier) {

  override fun getCodeBlock(startElement: PsiElement): PsiCodeBlock? {
    val callExpression = startElement.parentOfType<PsiMethodCallExpression>() ?: return null
    val lambdaExpression = callExpression.argumentList.expressions.filterIsInstance<PsiLambdaExpression>().firstOrNull() ?: return null
    return CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock(lambdaExpression)
  }
}

internal abstract class AbstractInsertCancellationCheckInLoopFix(
  private val cancellationCheckCallFqn: String,
  anchor: PsiElement,
) : LocalQuickFixOnPsiElement(anchor) {

  override fun getFamilyName(): String = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  override fun getText(): String = familyName

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val codeBlock = getCodeBlock(startElement) ?: return
    val cancellationCheckStatement = createCancellationCheckStatement(project, startElement)
    codeBlock.addBefore(cancellationCheckStatement, codeBlock.firstBodyElement)?.also {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(it)
    }
  }

  abstract fun getCodeBlock(startElement: PsiElement): PsiCodeBlock?

  private fun createCancellationCheckStatement(project: Project, startElement: PsiElement): PsiStatement {
    val psiElementFactory = PsiElementFactory.getInstance(project)
    val cancellationCheckText = "${cancellationCheckCallFqn}();"
    return psiElementFactory.createStatementFromText(cancellationCheckText, startElement)
  }
}
