// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInsight.BlockUtils.expandSingleStatementToBlockStatement
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle


private val EP_NAME: ExtensionPointName<CancellationCheckInLoopsFixProvider> = ExtensionPointName.create(
  "DevKit.lang.cancellationCheckInLoopsFixProvider")

internal object CancellationCheckInLoopsFixProviders : LanguageExtension<CancellationCheckInLoopsFixProvider>(EP_NAME.name)


@IntellijInternalApi
@ApiStatus.Internal
interface CancellationCheckInLoopsFixProvider {

  fun getFixes(loopKeyword: PsiElement, cancellationCheckFqn: String): List<LocalQuickFix>

}


internal class JavaCancellationCheckInLoopsFixProvider : CancellationCheckInLoopsFixProvider {

  override fun getFixes(loopKeyword: PsiElement, cancellationCheckFqn: String): List<LocalQuickFix> {
    return listOf(InsertCancellationCheckFix(cancellationCheckFqn, loopKeyword))
  }

}


internal class InsertCancellationCheckFix(
  private val cancellationCheckCallFqn: String,
  loopKeyword: PsiElement,
) : LocalQuickFixOnPsiElement(loopKeyword) {

  override fun getFamilyName(): String = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  override fun getText(): String = familyName

  override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return startElement.parentOfType<PsiLoopStatement>() != null
  }

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val loopStatement = startElement.parentOfType<PsiLoopStatement>() ?: return
    val factory = PsiElementFactory.getInstance(project)

    val cancellationCheckText = "${cancellationCheckCallFqn}();"
    val cancellationCheckStatement = factory.createStatementFromText(cancellationCheckText, loopStatement)

    val body = loopStatement.body ?: return
    val bodyBlock = expandSingleStatementToBlockStatement(body).parentOfType<PsiBlockStatement>(withSelf = true)

    val insertedElement = bodyBlock?.codeBlock?.addBefore(cancellationCheckStatement, bodyBlock.codeBlock.firstBodyElement)

    insertedElement?.let {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(it)
    }
  }

}
