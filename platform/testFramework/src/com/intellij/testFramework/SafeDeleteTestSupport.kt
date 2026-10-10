// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.model.SymbolPointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.ModPlanUiInterpreter
import com.intellij.refactoring.modplan.impl.ModPlanFlowUi
import com.intellij.refactoring.modplan.impl.ModPlanTestFlowUi
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageTarget
import org.jetbrains.annotations.TestOnly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Function
import kotlin.io.path.exists

/**
 * Runs one safe delete test data set through the legacy flow, the interactive ModPlan flow and the headless ModPlan flow with the same
 * answers, and records the non-code usages in comments, strings and non-code files that each flow finds.
 *
 * A non-code usage is recorded only for a deletion with no conflict: the conflicts list the non-code usages in their messages, and no flow
 * shows a non-code usage after the user accepts the conflicts.
 */
@TestOnly
object SafeDeleteTestSupport {
  /**
   * Gives [answers] to the flows that ask nothing in a unit test, until [disposable] is disposed:
   * - the search options go to [RefactoringSettings], which the legacy flow and the interactive ModPlan flow read; an option that
   *   [answers] does not set is false, as in the headless flow;
   * - all the answers go to each chain of the legacy flow, through [ModPlanUiInterpreter], on top of the answers that the legacy flow
   *   gives in a unit test, and to the interactive ModPlan flow, through [ModPlanSafeDeleteFlow.answerInTests].
   *
   * @return the non-code usages that the two flows show in their previews
   */
  @JvmStatic
  fun applyOptions(answers: Map<ModPlanOption<*>, Any>, disposable: Disposable): NonCodeUsageRecorder {
    val settings = RefactoringSettings.getInstance()
    val oldSearchInComments = settings.SAFE_DELETE_SEARCH_IN_COMMENTS
    val oldSearchInNonCode = settings.SAFE_DELETE_SEARCH_IN_NON_JAVA
    settings.SAFE_DELETE_SEARCH_IN_COMMENTS = answers[ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS] == true
    settings.SAFE_DELETE_SEARCH_IN_NON_JAVA = answers[ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE] == true
    Disposer.register(disposable) {
      settings.SAFE_DELETE_SEARCH_IN_COMMENTS = oldSearchInComments
      settings.SAFE_DELETE_SEARCH_IN_NON_JAVA = oldSearchInNonCode
    }

    val recorder = NonCodeUsageRecorder()
    BaseRefactoringProcessor.recordPreviewInTests({ usages -> recorder.addUsages(usages.filter { it.isNonCodeUsage }) }, disposable)
    val application = ApplicationManager.getApplication()
    application.replaceService(ModPlanUiInterpreter::class.java, AnsweringInterpreter(ModPlanUiInterpreter.getInstance(), answers), disposable)
    application.replaceService(ModPlanFlowUi::class.java, RecordingFlowUi(recorder), disposable)
    ModPlanSafeDeleteFlow.answerInTests({ optionValues -> optionValues.withAll(answers) }, disposable)
    return recorder
  }

  /**
   * Safe deletes [elements] in one request through [ModPlanSafeDeleteRefactoring] and [ModPlanExecutor], as the MCP tool does: the
   * analysis on a background thread, so a question to the user or a modal progress fails the test.
   *
   * A refused deletion changes nothing. A plan with a conflict throws [BaseRefactoringProcessor.ConflictsInTestsException] with the
   * messages of the conflicts and the non-code usages, as the interactive safe delete reports them, unless [ignoreConflicts] writes it
   * anyway, as `force` does.
   *
   * @param recorder gets the non-code usages of a plan with no conflict, before the write
   * @return the plan of the deletion
   */
  @JvmStatic
  @JvmOverloads
  fun headlessSafeDelete(
    project: Project,
    elements: List<PsiElement>,
    optionValues: ModPlanOptionValues,
    ignoreConflicts: Boolean = false,
    recorder: NonCodeUsageRecorder? = null,
  ): ModPlan {
    val plan = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
      runReadActionBlocking { ModPlanSafeDeleteRefactoring.getInstance().plan(ModPlanRequest(project, elements.map(SymbolPointer::of), optionValues)) }
    }))
    // The interactive safe delete does nothing for an element that it refuses, so the test checks that nothing changed.
    if (plan is ModPlan.RefusedPlan) return plan
    plan as ModPlan.AcceptedPlan
    if (plan.conflicts.needsConfirmation()) {
      if (!ignoreConflicts) throw BaseRefactoringProcessor.ConflictsInTestsException(messagesOf(plan))
    }
    else if (recorder != null) {
      runReadActionBlocking {
        plan.conflicts.nonCodeUsages().forEach { nonCodeUsage -> nonCodeUsage.location?.let { recorder.add(it.virtualFile, it.range?.let { range -> TextRange.create(range) }) } }
      }
    }
    assertTrue(ModPlanExecutor.execute(plan.execution, true) is ModPlanExecutionResult.Applied)
    return plan
  }

  /**
   * Checks the non-code usages of a deletion with no conflict against [nonCodeUsageFile]. If [answers] turn on a search in comments or in
   * non-code files, the file is required, and an empty file expects no non-code usage. Without a search, a missing file expects no
   * non-code usage, and a file lists the non-code usages that need no search, such as the `exports` of an emptied Java package.
   */
  @JvmStatic
  fun checkNonCodeUsages(recorder: NonCodeUsageRecorder, answers: Map<ModPlanOption<*>, Any>, nonCodeUsageFile: Path) {
    val searches = answers[ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS] == true || answers[ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE] == true
    if (searches || nonCodeUsageFile.exists()) {
      UsefulTestCase.assertSameLinesWithFile(nonCodeUsageFile.toString(), recorder.text())
    }
    else {
      assertEquals("non-code usages without a search", emptyList<String>(), recorder.lines())
    }
  }

  /** @return [this] with each of [answers] as an explicit value */
  @JvmStatic
  fun ModPlanOptionValues.withAll(answers: Map<ModPlanOption<*>, Any>): ModPlanOptionValues =
    answers.entries.fold(this) { optionValues, (option, value) ->
      @Suppress("UNCHECKED_CAST")
      optionValues.with(option as ModPlanOption<Any>, value)
    }

  private fun messagesOf(plan: ModPlan.AcceptedPlan): List<String> {
    val conflictMessages = plan.conflicts.conflicts.filter { it.kind != ModPlanConflictKind.NON_CODE_USAGE }.flatMap { it.messages }
    val nonCodeUsageMessages = plan.conflicts.nonCodeUsages().flatMap { it.messages }
    return conflictMessages + nonCodeUsageMessages
  }

  /** Runs each chain of [delegate] with [answers] on top of the options of its request, so that they answer its questions. */
  private class AnsweringInterpreter(private val delegate: ModPlanUiInterpreter, private val answers: Map<ModPlanOption<*>, Any>) : ModPlanUiInterpreter {
    override fun <T> interpret(
      title: @NlsContexts.DialogTitle String,
      request: ModPlanRequest,
      chain: Function<ModPlanChainBuilder, ModPlanChain<T>>,
    ): ModPlanChain.ChainResult.Finished<T>? =
      delegate.interpret(title, ModPlanChain.withOptionValues(request, request.optionValues().withAll(answers)), chain)
  }

  /** The UI of the unit-test mode, with the non-code usages of its preview given to [recorder]. */
  private class RecordingFlowUi(private val recorder: NonCodeUsageRecorder) : ModPlanTestFlowUi() {
    override fun previewNonCodeUsages(
      project: Project,
      refactoring: ModPlanRefactoring,
      commandName: @NlsContexts.Command String,
      targets: Array<out UsageTarget>,
      usages: Array<UsageInfo>,
      proceed: (previewAgain: Boolean) -> Unit,
    ): Boolean {
      recorder.addUsages(usages.toList())
      return false
    }
  }

  /**
   * The non-code usages that a deletion found, as the lines of a `.nonCodeUsages` file: `<file name>:<line>: <text of the line>`, sorted,
   * one line for each place. The places are read when the non-code usage is added, before the write changes them.
   */
  class NonCodeUsageRecorder {
    private val nonCodeUsageLines = sortedSetOf<String>()

    /** @return the lines of the non-code usages, sorted */
    fun lines(): List<String> = nonCodeUsageLines.toList()

    /** @return the lines of the non-code usages, as the text of a `.nonCodeUsages` file */
    fun text(): String = nonCodeUsageLines.joinToString("\n")

    internal fun addUsages(usages: Collection<UsageInfo>) {
      runReadActionBlocking {
        usages.forEach { usage -> add(usage.virtualFile, usage.segment?.let { TextRange.create(it) }) }
      }
    }

    internal fun add(file: VirtualFile?, range: TextRange?) {
      if (file == null) return
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return
      val line = document.getLineNumber(range?.startOffset ?: 0)
      val lineText = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))).trim()
      nonCodeUsageLines.add("${file.name}:${line + 1}: $lineText")
    }
  }
}
