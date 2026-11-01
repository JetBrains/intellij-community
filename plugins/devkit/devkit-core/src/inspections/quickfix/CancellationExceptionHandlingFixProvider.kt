// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.LanguageExtension
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiCatchSection
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiParameter
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UElement

/**
 * Java call form of `com.intellij.diagnostic.rethrowControlFlowException`; `controlFlowExceptions.kt` compiles to the
 * `ControlFlowExceptionsKt` facade class.
 */
private const val RETHROW_CONTROL_FLOW_EXCEPTION_JAVA_FQN: String =
  "com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException"

private val EP_NAME: ExtensionPointName<CancellationExceptionHandlingFixProvider> =
  ExtensionPointName.create("DevKit.lang.cancellationExceptionHandlingFixProvider")

internal object CancellationExceptionHandlingFixProviders : LanguageExtension<CancellationExceptionHandlingFixProvider>(EP_NAME.name)

/**
 * Supplies the language-specific PSI edits behind `IncorrectCancellationExceptionHandlingInspection`.
 *
 * The inspections detect on UAST (language-agnostic), but the edits are language-specific, so each fix is produced here.
 * A method returns null when the fix does not apply to the given element.
 */
@ApiStatus.Internal
interface CancellationExceptionHandlingFixProvider {

  /**
   * Deletes the statement that logs the caught cancellation exception; available only for a statement directly in a
   * code block.
   */
  fun getRemoveLoggingStatementFix(loggingCall: PsiElement): LocalQuickFix?

  /**
   * Inserts `rethrowControlFlowException(<caught>)` as the first statement of the enclosing catch body.
   */
  fun getInsertRethrowControlFlowExceptionFix(caughtExceptionParameter: PsiElement): LocalQuickFix?
}

/**
 * Fixes that the provider registered for the language of [element] creates for the source PSI of [element].
 */
internal fun cancellationExceptionHandlingFixes(
  element: UElement,
  createFix: CancellationExceptionHandlingFixProvider.(PsiElement) -> LocalQuickFix?,
): Array<LocalQuickFix> {
  val sourcePsi = element.sourcePsi ?: return LocalQuickFix.EMPTY_ARRAY
  val provider = CancellationExceptionHandlingFixProviders.forLanguage(element.lang) ?: return LocalQuickFix.EMPTY_ARRAY
  val fix = provider.createFix(sourcePsi) ?: return LocalQuickFix.EMPTY_ARRAY
  return arrayOf(fix)
}

internal class JavaCancellationExceptionHandlingFixProvider : CancellationExceptionHandlingFixProvider {

  override fun getRemoveLoggingStatementFix(loggingCall: PsiElement): LocalQuickFix? {
    val statement = loggingCall.parent as? PsiExpressionStatement ?: return null
    if (statement.parent !is PsiCodeBlock) return null
    return LocalQuickFix.from(JavaRemoveLoggingStatementFix(statement))
  }

  override fun getInsertRethrowControlFlowExceptionFix(caughtExceptionParameter: PsiElement): LocalQuickFix? {
    val parameter = caughtExceptionParameter as? PsiParameter ?: return null
    return LocalQuickFix.from(JavaInsertRethrowHelperFix(parameter))
  }
}

private class JavaRemoveLoggingStatementFix(statement: PsiExpressionStatement)
  : PsiUpdateModCommandAction<PsiExpressionStatement>(statement) {

  override fun getFamilyName(): String {
    return DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name")
  }

  override fun invoke(context: ActionContext, element: PsiExpressionStatement, updater: ModPsiUpdater) {
    element.delete()
  }
}

private class JavaInsertRethrowHelperFix(parameter: PsiParameter) : PsiUpdateModCommandAction<PsiParameter>(parameter) {

  override fun getFamilyName(): String {
    return DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")
  }

  override fun invoke(context: ActionContext, element: PsiParameter, updater: ModPsiUpdater) {
    val catchBlock = element.parentOfType<PsiCatchSection>()?.catchBlock ?: return
    val project = context.project()
    val statement = PsiElementFactory.getInstance(project)
      .createStatementFromText("$RETHROW_CONTROL_FLOW_EXCEPTION_JAVA_FQN(${element.name});", catchBlock)
    val firstBodyElement = catchBlock.firstBodyElement
    val inserted = if (firstBodyElement != null) catchBlock.addBefore(statement, firstBodyElement) else catchBlock.add(statement)
    val shortened = JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted)
    CodeStyleManager.getInstance(project).reformat(shortened)
  }
}
