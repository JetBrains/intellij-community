// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.impl.ModPlanFlow
import com.intellij.refactoring.modplan.impl.ModPlanFlowUi
import com.intellij.refactoring.modplan.impl.ModPlanTestFlowUi
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageTarget
import com.intellij.util.containers.MultiMap

/** The preview with excluded usages, the conflicts and the refusals of [ModPlanFlow], on a refactoring of a text file. */
class ModPlanFlowTest : BasePlatformTestCase() {
  private val answered = ModPlanOption.bool("answered", false, RefactoringBundle.messagePointer("preview.button"))
  private val question = object : ModPlanQuestion.Custom {
    override fun options(): List<ModPlanOption<*>> = listOf(answered)

    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues = currentValues.with(answered, true)
  }
  private val ui = TestFlowUi()
  private var plannedRuns = 0

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().replaceService(ModPlanFlowUi::class.java, ui, testRootDisposable)
  }

  fun `test the preview shows the usages and writes the plan it shows`() {
    val file = myFixture.addFileToProject("a.txt", "one two")

    assertEquals(ModPlanFlow.Result.PREVIEW_SHOWN, run(file, UpperCaseRefactoring(null, false), ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.PREVIEW, true)))
    assertEquals(2, ui.previewedUsages.size)
    assertEquals("one two", file.text)

    ui.proceedFromPreview!!(false, emptySet())

    assertEquals("ONE TWO", file.text)
    assertEquals(1, plannedRuns)
  }

  fun `test an excluded usage stays as it is`() {
    val file = myFixture.addFileToProject("a.txt", "one two")
    run(file, UpperCaseRefactoring(null, false), ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.PREVIEW, true))

    ui.proceedFromPreview!!(false, setOf(ui.previewedUsages.first()))

    assertEquals("one TWO", file.text)
    // The final step runs again for the exclusion.
    assertEquals(2, plannedRuns)
  }

  fun `test an excluded usage after a change of the file shows the error and writes nothing`() {
    val file = myFixture.addFileToProject("a.txt", "one two")
    run(file, UpperCaseRefactoring(null, false), ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.PREVIEW, true))
    val previewedUsage = ui.previewedUsages.first()
    WriteCommandAction.runWriteCommandAction(project) {
      val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
      document.insertString(0, "zero ")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java, RefactoringBundle.getCannotRefactorMessage(null)) {
      ui.proceedFromPreview!!(false, setOf(previewedUsage))
    }

    assertEquals("zero one two", file.text)
    assertEquals(1, plannedRuns)
  }

  fun `test cancelled conflicts ask the questions again`() {
    val file = myFixture.addFileToProject("a.txt", "one two")
    ui.conflictAnswers.addAll(listOf(false, true))

    assertEquals(ModPlanFlow.Result.DONE, run(file, UpperCaseRefactoring(question, true), ModPlanOptionValues.EMPTY))

    assertEquals(2, ui.askedQuestions)
    assertEquals("ONE TWO", file.text)
  }

  fun `test cancelled conflicts without questions cancel the refactoring`() {
    val file = myFixture.addFileToProject("a.txt", "one two")
    ui.conflictAnswers.add(false)

    assertEquals(ModPlanFlow.Result.CANCELLED, run(file, UpperCaseRefactoring(null, true), ModPlanOptionValues.EMPTY))

    assertEquals(0, ui.askedQuestions)
    assertEquals("one two", file.text)
  }

  fun `test the conflicts before the preview are not confirmed again`() {
    val file = myFixture.addFileToProject("a.txt", "one two")
    ui.conflictAnswers.add(true)
    run(file, UpperCaseRefactoring(null, true), ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.PREVIEW, true))

    ui.proceedFromPreview!!(false, emptySet())

    assertEquals("ONE TWO", file.text)
  }

  fun `test the conflicts before the preview are not confirmed again after an excluded usage`() {
    val file = myFixture.addFileToProject("a.txt", "one two")
    ui.conflictAnswers.add(true)
    run(file, UpperCaseRefactoring(null, true), ModPlanOptionValues.EMPTY.with(ModPlanRefactoring.PREVIEW, true))

    ui.proceedFromPreview!!(false, setOf(ui.previewedUsages.first()))

    assertEquals("one TWO", file.text)
    assertEquals(2, plannedRuns)
  }

  fun `test a refusal shows its message`() {
    val file = myFixture.addFileToProject("a.txt", "one two")

    assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java, RefactoringBundle.getCannotRefactorMessage("No words")) {
      run(file, UpperCaseRefactoring(null, false, "No words"), ModPlanOptionValues.EMPTY)
    }
    assertEquals("one two", file.text)
  }

  private fun run(file: PsiFile, refactoring: UpperCaseRefactoring, optionValues: ModPlanOptionValues): ModPlanFlow.Result {
    val request = runReadActionBlocking { ModPlanRequest(project, listOf(SymbolPointer.of(file)), optionValues) }
    return ModPlanFlow.run(refactoring, request, true) {}
  }

  /**
   * Writes "one" and "two" of the file of the request in upper case, but the excluded ones; each word is a usage. It asks [question]
   * first, and a conflict comes with the plan if [hasConflict]. It refuses with the message [refusal] if there is one.
   */
  private inner class UpperCaseRefactoring(
    private val question: ModPlanQuestion?,
    private val hasConflict: Boolean,
    private val refusal: String? = null,
  ) : ModPlanRefactoring {
    override fun id(): String = "refactoring.test"

    override fun title(): String = "Upper Case"

    override fun targetsNodeText(): String = "Words"

    override fun isSupported(request: ModPlanRequest): Boolean = true

    override fun chain(chainBuilder: ModPlanChainBuilder): ModPlanChain<ModPlanFinalStep<ModPlan>> {
      return chainBuilder.ask(question) { afterAnswers -> afterAnswers.finish { request -> planOf(request) } }
    }

    private fun planOf(request: ModPlanRequest): ModPlan {
      plannedRuns++
      val file = request.subjects().single().element() as PsiFile
      if (refusal != null) return ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, file, refusal)
      val usages = LinkedHashMap<SmartPsiFileRange, List<ModPlanEdit>>()
      for (word in listOf("one", "two")) {
        val location = file.createSmartFileRange(TextRange.from(file.text.indexOf(word), word.length))!!
        usages[location] = listOf(ModPlanEdit.update(file) { copy ->
          (copy.firstChild as LeafElement).replaceWithText(copy.text.replace(word, word.uppercase()))
        })
      }
      val conflicts = if (hasConflict) ModPlanConflicts.builder().fixable(file, "conflict").build() else ModPlanConflicts.EMPTY
      val keptUsages = usages.filterKeys { !ModPlanRefactoring.isExcluded(request, it) }
      return ModPlan.createPlan(keptUsages.values.flatten(), conflicts, "Upper case", id(), file, false, keptUsages.keys.toList())
    }
  }

  /** Answers the questions, answers the conflicts from [conflictAnswers], and keeps the preview to go on from it later. */
  private class TestFlowUi : ModPlanTestFlowUi() {
    val conflictAnswers = ArrayDeque<Boolean>()
    var askedQuestions = 0
    var previewedUsages: List<SmartPsiFileRange> = emptyList()
    var proceedFromPreview: ((Boolean, Set<SmartPsiFileRange>) -> Unit)? = null

    override fun ask(project: Project, title: String, question: ModPlanQuestion, initialValues: ModPlanOptionValues): ModPlanOptionValues? {
      askedQuestions++
      return (question as ModPlanQuestion.Custom).ask(project, initialValues)
    }

    override fun confirmConflicts(project: Project, conflicts: MultiMap<PsiElement, String>): Boolean = conflictAnswers.removeFirst()

    override fun previewUsages(
      project: Project,
      refactoring: ModPlanRefactoring,
      targets: Array<out UsageTarget>,
      plan: ModPlan.AcceptedPlan,
      nonCodeUsages: Array<UsageInfo>,
      proceed: (previewAgain: Boolean, excludedUsages: Set<SmartPsiFileRange>) -> Unit,
    ): Boolean {
      previewedUsages = plan.usages
      proceedFromPreview = proceed
      return true
    }
  }
}
