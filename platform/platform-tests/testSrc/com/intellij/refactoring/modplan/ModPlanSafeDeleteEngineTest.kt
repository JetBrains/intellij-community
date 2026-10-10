// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModDisplayMessage
import com.intellij.modcommand.ModShowConflicts
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.modplan.impl.ModPlanFlowUi
import com.intellij.refactoring.modplan.impl.ModPlanTestFlowUi
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ModPlanTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ModPlanSafeDeleteEngineTest : BasePlatformTestCase() {
  private val logEntries = ArrayList<String>()
  private val readByProvider = ModPlanOption.bool("readByProvider", false) { "Read by the provider" }

  private class FakeProvider(
    private val isApplicableTo: (PsiElement) -> Boolean,
    private val expandedElementsOf: (PsiElement) -> List<PsiElement>,
    private val subjectQuestionFor: (PsiElement) -> ModPlanQuestion?,
    private val usageQuestionFor: (PsiElement) -> ModPlanQuestion?,
    private val analysis: (PsiElement, ModPlanRequest) -> ModPlanAnalysisResult,
    private val isLegacyOnlyFor: (PsiElement) -> Boolean,
    private val replaces: (PsiElement) -> Boolean,
    private val startedElements: MutableList<PsiElement>,
  ) : ModPlanSafeDeleteProvider {
    override fun isApplicable(subject: SymbolPointer): Boolean = subject.element()?.let(isApplicableTo) == true

    override fun isLegacyOnly(subject: SymbolPointer): Boolean = subject.element()?.let(isLegacyOnlyFor) ?: true

    override fun prepareRequest(
      chainBuilder: ModPlanChainBuilder,
      subject: SymbolPointer,
      deletedSubjects: List<SymbolPointer>,
    ): ModPlanChain<ModPlanSafeDeleteProvider.DeletionDecision> {
      val element =
        subject.element() ?: return chainBuilder.passValue(ModPlanSafeDeleteProvider.DeletionDecision.REFUSED)
      startedElements.add(element)
      if (subject !in chainBuilder.request.subjects()) return chainBuilder.passValue(ModPlanSafeDeleteProvider.DeletionDecision.DELETED)
      val alsoDeleted = expandedElementsOf(element).map { SymbolPointer.of(it) }
      val preparation = if (replaces(element)) {
        ModPlanSafeDeleteProvider.DeletionDecision.replacedBy(alsoDeleted)
      }
      else {
        ModPlanSafeDeleteProvider.DeletionDecision.deletedWith(alsoDeleted)
      }
      return chainBuilder.ask(subjectQuestionFor(element)) { it.passValue(preparation) }
    }

    override fun analyze(
      chainBuilder: ModPlanChainBuilder,
      subject: SymbolPointer,
      deletedSubjects: List<SymbolPointer>,
    ): ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> {
      val element = subject.element()!!
      return chainBuilder.ask(usageQuestionFor(element)) { answered ->
        val request = answered.request
        answered.finish { analysis(subject.element()!!, request) }
      }
    }
  }

  /** The elements that the fake provider was asked to prepare in [ModPlanSafeDeleteProvider.prepareRequest]. */
  private val startedElements = ArrayList<PsiElement>()

  private fun install(
    isApplicableTo: (PsiElement) -> Boolean = { true },
    expand: (PsiElement) -> List<PsiElement> = { emptyList() },
    subjectQuestion: (PsiElement) -> ModPlanQuestion? = { null },
    usageQuestion: (PsiElement) -> ModPlanQuestion? = { null },
    isLegacyOnlyFor: (PsiElement) -> Boolean = { false },
    replaces: (PsiElement) -> Boolean = { false },
    analysis: (PsiElement, ModPlanRequest) -> ModPlanAnalysisResult,
  ) {
    ExtensionTestUtil.maskExtensions(
      ModPlanSafeDeleteProvider.EP_NAME,
      listOf(FakeProvider(isApplicableTo, expand, subjectQuestion, usageQuestion, analysis, isLegacyOnlyFor, replaces, startedElements)),
      testRootDisposable,
    )
  }

  /**
   * @param answers answers the questions that the run asks
   * @return the questions that a headless run of [request] asks, in their order; the step of the plan runs after them
   */
  private fun askedQuestionsOf(
    request: ModPlanRequest,
    answers: (ModPlanOptionValues) -> ModPlanOptionValues = { it },
  ): List<ModPlanQuestion> {
    val askedQuestions = ArrayList<ModPlanQuestion>()
    inBackground {
      ModPlanTestUtil.runAnswering(request, ModPlanSafeDeleteRefactoring.getInstance()::chain) { question, currentValues ->
        askedQuestions.add(question)
        answers(currentValues)
      }.run(request)
    }
    return askedQuestions
  }

  private fun askedQuestionsOf(target: PsiElement, answers: (ModPlanOptionValues) -> ModPlanOptionValues = { it }): List<ModPlanQuestion> =
    askedQuestionsOf(requestOf(target), answers)

  private fun nothingFound(): ModPlanAnalysisResult = ModPlanAnalysisResult.Found(emptyList(), ModPlanConflicts.EMPTY)

  private fun requestOf(target: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlanRequest =
    runReadActionBlocking { ModPlanRequest(project, listOf(SymbolPointer.of(target)), optionValues) }

  private fun plan(request: ModPlanRequest): ModPlan =
    inBackground { ModPlanSafeDeleteRefactoring.getInstance().plan(request) }

  private fun plan(target: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan =
    plan(requestOf(target, optionValues))

  private fun ready(target: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan.AcceptedPlan {
    return plan(target, optionValues) as ModPlan.AcceptedPlan
  }

  private fun commandOf(target: PsiElement): ModCommand {
    val request = requestOf(target)
    return inBackground {
      ModPlanCommandAction(ModPlanSafeDeleteRefactoring.getInstance(),
                           request,
                           target,
                           "",
                           false).perform(ActionContext.from(null, target.containingFile))
    }
  }

  private fun refusalOf(target: PsiElement): ModPlan.RefusedPlan = plan(target) as ModPlan.RefusedPlan

  private fun isSupported(vararg targets: PsiElement): Boolean =
    runReadActionBlocking {
      ModPlanSafeDeleteRefactoring.getInstance()
        .isSupported(ModPlanRequest(project, targets.map(SymbolPointer::of), ModPlanOptionValues.EMPTY))
    }

  fun `test two edits in one file bind before writing`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install { _, _ ->
      ModPlanAnalysisResult.Found(
        listOf(ModPlanEdit.update(b) { logEntries.add("first edit") }, ModPlanEdit.update(b) { logEntries.add("second edit") }),
        ModPlanConflicts.EMPTY,
      )
    }

    val readyPlan = ready(a)

    assertEquals(listOf("first edit", "second edit"), logEntries)
    assertEquals(listOf(a.virtualFile), deletedFiles(readyPlan))
  }

  fun `test conflicts do not prevent the command`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { target, _ ->
      ModPlanAnalysisResult.Found(
        emptyList(),
        ModPlanConflicts.builder().broken(target, "broken").fixable(target, "fixable").build(),
      )
    }

    val readyPlan = ready(a)

    assertTrue(readyPlan.conflicts.hasBroken())
    assertEquals(listOf(a.virtualFile), deletedFiles(readyPlan))
    assertInstanceOf(commandOf(a).unpack().first(), ModShowConflicts::class.java)
  }

  fun `test deleting an element adds its file to the deleted files`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install { _, _ -> ModPlanAnalysisResult.Found(listOf(ModPlanEdit.delete(b)), ModPlanConflicts.EMPTY) }

    val readyPlan = ready(a)

    assertEquals(setOf(a.virtualFile, b.virtualFile), deletedFiles(readyPlan).toSet())
  }

  fun `test a replaced subject is neither analyzed nor deleted`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val analyzedElements = ArrayList<PsiElement>()
    install(expand = { if (it == a) listOf(b) else emptyList() }, replaces = { it == a }) { element, _ ->
      analyzedElements.add(element)
      nothingFound()
    }

    val readyPlan = ready(a)

    assertEquals(listOf(a, b), startedElements)
    assertEquals(listOf<PsiElement>(b), analyzedElements)
    assertEquals(listOf(b.virtualFile), deletedFiles(readyPlan))
  }

  fun `test a subject that is not replaced is deleted with what it adds`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install(expand = { if (it == a) listOf(b) else emptyList() }) { _, _ -> nothingFound() }

    val readyPlan = ready(a)

    assertEquals(setOf(a.virtualFile, b.virtualFile), deletedFiles(readyPlan).toSet())
  }

  fun `test command is the writes when there are no conflicts`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { _, _ -> nothingFound() }

    val command = commandOf(a)

    assertEquals(ready(a).execution.command.unpack(), command.unpack())
  }

  fun `test command starts with the conflicts`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { target, _ -> ModPlanAnalysisResult.Found(emptyList(), ModPlanConflicts.builder().broken(target, "broken").build()) }

    assertInstanceOf(commandOf(a).unpack().first(), ModShowConflicts::class.java)
  }

  fun `test a refused deletion gives an error command`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { _, _ -> ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED) }

    val command = commandOf(a)

    assertEquals(ModDisplayMessage.MessageKind.ERROR, (command as ModDisplayMessage).kind)
  }

  fun `test command joins with another change`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val c = myFixture.addFileToProject("c.txt", "c")
    install { _, _ -> nothingFound() }
    val change = ModUpdateFileText(c.virtualFile, "c", "c2", emptyList())

    val command = commandOf(a).andThen(change)
    var result: ModCommandExecutor.BatchExecutionResult? = null
    CommandProcessor.getInstance().executeCommand(project, {
      result = ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, c), command)
    }, "Test command", null)

    assertEquals(ModCommandExecutor.Result.SUCCESS, result)
    assertFalse(a.virtualFile.isValid)
    assertEquals("c2", runReadAction { FileDocumentManager.getInstance().getDocument(c.virtualFile)!!.text })
  }

  fun `test non-code usages are reported and not edited`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install { target, _ ->
      ModPlanAnalysisResult.Found(emptyList(),
                                  ModPlanConflicts.builder().nonCodeUsage(b, null, "nonCodeUsages ${(target as PsiFile).name}").build())
    }

    val readyPlan = ready(a)

    val nonCodeUsage = readyPlan.conflicts.nonCodeUsages().single()
    assertEquals(ModPlanConflictKind.NON_CODE_USAGE, nonCodeUsage.kind)
    assertEquals(b.virtualFile, nonCodeUsage.location!!.virtualFile)
    assertEquals(listOf("nonCodeUsages a.txt"), nonCodeUsage.messages)
    assertFalse(readyPlan.conflicts.needsConfirmation())
    assertEquals(listOf(a.virtualFile), deletedFiles(readyPlan))
  }

  fun `test a refused analysis refuses the plan`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { _, _ -> ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED) }

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusalOf(a).reason)
  }

  fun `test isLegacyOnly true refuses with NOT_SUPPORTED before the search`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install(
      expand = { logEntries.add("expand"); emptyList() },
      isLegacyOnlyFor = { true },
    ) { _, _ -> logEntries.add("analyze"); nothingFound() }

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusalOf(a).reason)
    assertEquals(emptyList<String>(), logEntries)
    assertFalse(isSupported(a))
  }

  fun `test a usage question of an option answered before is not asked again`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install(
      expand = { if (it == a) listOf(b) else emptyList() },
      usageQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) },
    ) { _, _ -> nothingFound() }

    val questions = askedQuestionsOf(a) { it.with(readByProvider, true) }

    assertEquals(listOf<ModPlanQuestion>(ModPlanQuestion.Form.of(listOf(readByProvider), null)), questions)
  }

  fun `test the analyses run after the questions of every subject`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val askedOfB = ModPlanOption.bool("askedOfB", false) { "Asked of b" }
    install(
      expand = { if (it == a) listOf(b) else emptyList() },
      usageQuestion = { ModPlanQuestion.Form.of(listOf(if (it == a) { readByProvider } else { askedOfB }), null) },
    ) { element, _ -> logEntries.add("analyze ${element.containingFile.name}"); nothingFound() }

    askedQuestionsOf(a) { values ->
      logEntries.add("question")
      if (values.has(readByProvider)) values.with(askedOfB, true) else values.with(readByProvider, true)
    }

    assertEquals(listOf("question", "question", "analyze a.txt", "analyze b.txt"), logEntries)
  }

  fun `test a question that the request answers is not asked`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install(subjectQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) }) { _, _ -> nothingFound() }

    val questions = askedQuestionsOf(requestOf(a, ModPlanOptionValues.EMPTY.with(readByProvider, false)))

    assertEquals(emptyList<ModPlanQuestion>(), questions)
  }

  fun `test a step reads the answers of the questions before it`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install(subjectQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) }) { _, scope ->
      logEntries.add("read ${scope.optionValues().get(readByProvider)}")
      nothingFound()
    }

    askedQuestionsOf(a) { it.with(readByProvider, true) }

    assertEquals(listOf("read true"), logEntries)
  }

  fun `test subject questions are asked for the requested subjects only`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val customOfA = FakeCustomQuestion()
    val customOfB = FakeCustomQuestion()
    install(
      expand = { if (it == a) listOf(b) else emptyList() },
      subjectQuestion = { if (it == a) customOfA else customOfB },
    ) { _, _ -> nothingFound() }

    val questions = askedQuestionsOf(a)

    assertEquals(listOf<ModPlanQuestion>(customOfA), questions)
  }

  fun `test the generic provider keeps the search optionValues in the common settings`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    ExtensionTestUtil.maskExtensions(ModPlanSafeDeleteProvider.EP_NAME, emptyList(), testRootDisposable)
    val settings = RefactoringSettings.getInstance()
    val oldInComments = settings.SAFE_DELETE_SEARCH_IN_COMMENTS
    val oldInNonJava = settings.SAFE_DELETE_SEARCH_IN_NON_JAVA
    try {
      val storage = askedQuestionsOf(a).filterIsInstance<ModPlanQuestion.Form>()
        .first { ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS in it.options() }.storage()!!
      settings.SAFE_DELETE_SEARCH_IN_COMMENTS = false
      assertFalse(storage.load(ModPlanOptionValues.EMPTY).get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))

      storage.store(ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true)
                      .with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, true))

      assertTrue(settings.SAFE_DELETE_SEARCH_IN_COMMENTS)
      assertTrue(settings.SAFE_DELETE_SEARCH_IN_NON_JAVA)
    }
    finally {
      settings.SAFE_DELETE_SEARCH_IN_COMMENTS = oldInComments
      settings.SAFE_DELETE_SEARCH_IN_NON_JAVA = oldInNonJava
    }
  }

  private class FakeCustomQuestion : ModPlanQuestion.Custom {
    override fun options(): List<ModPlanOption<*>> = emptyList()

    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues = currentValues
  }

  fun `test a subject is started once in a run`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install(
      expand = { if (it == a) listOf(b) else emptyList() },
      subjectQuestion = { ModPlanQuestion.Form.of(listOf(readByProvider), null) },
      usageQuestion = { FakeCustomQuestion() },
    ) { _, _ -> nothingFound() }

    askedQuestionsOf(a) { it.with(readByProvider, true) }

    assertEquals(listOf(a, b), startedElements)
  }

  fun `test a headless plan starts a subject once`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { _, _ -> nothingFound() }

    plan(a, ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true))

    assertEquals(listOf(a), startedElements)
  }

  /**
   * Under a read lock the interpreter runs the chain in the background under the progress of the legacy flow, which shares the lock; under a
   * write action it runs the chain on the EDT.
   */
  fun `test the interpreter runs the chain under a read lock and under a write action`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val chainRunsOnEdt = ArrayList<Boolean>()
    // The usage question is made in the chain; the analysis runs in the step of the plan, which the caller runs after the interpreter.
    install(usageQuestion = { chainRunsOnEdt.add(ApplicationManager.getApplication().isDispatchThread); null }) { _, _ -> nothingFound() }
    val request = requestOf(a)
    val interpreter = ModPlanUiInterpreter.getInstance()

    val readPlan =
      runReadActionBlocking { interpreter.interpret("Safe Delete", request, ModPlanSafeDeleteRefactoring.getInstance()::chain) }
    val writePlan = WriteAction.compute<ModPlanChain.ChainResult.Finished<ModPlanFinalStep<ModPlan>>?, RuntimeException> {
      interpreter.interpret("Safe Delete", request, ModPlanSafeDeleteRefactoring.getInstance()::chain)
    }

    assertInstanceOf(runReadActionBlocking { readPlan?.value?.run(request) }, ModPlan.AcceptedPlan::class.java)
    assertInstanceOf(runReadActionBlocking { writePlan?.value?.run(request) }, ModPlan.AcceptedPlan::class.java)
    assertEquals(listOf(false, true), chainRunsOnEdt)
  }

  fun `test a change of PSI at a question runs the chain again without asking it again`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val c = myFixture.addFileToProject("c.txt", "c")
    val writingQuestion = WritingQuestion(c)
    install(subjectQuestion = { writingQuestion }) { _, scope ->
      logEntries.add("read ${scope.optionValues().get(readByProvider)}")
      nothingFound()
    }
    ApplicationManager.getApplication().replaceService(ModPlanFlowUi::class.java, CustomAskingFlowUi(), testRootDisposable)
    val request = requestOf(a)

    val planStep = ModPlanUiInterpreter.getInstance().interpret("Safe Delete", request, ModPlanSafeDeleteRefactoring.getInstance()::chain)
      ?.value

    assertInstanceOf(runReadActionBlocking { planStep?.run(request) }, ModPlan.AcceptedPlan::class.java)
    assertEquals(1, writingQuestion.askCount)
    assertEquals(listOf(a, a), startedElements)
    assertEquals(listOf("read true"), logEntries)
  }

  fun `test a write action that interrupts the steps after a question runs the chain again from its start`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val answeringQuestion = object : ModPlanQuestion.Custom {
      override fun options(): List<ModPlanOption<*>> = listOf(readByProvider)

      override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues =
        currentValues.with(readByProvider, true)
    }
    ApplicationManager.getApplication().replaceService(ModPlanFlowUi::class.java, CustomAskingFlowUi(), testRootDisposable)
    val interruptedReads = AtomicInteger()
    val collectedEntries =
      ModPlanUiInterpreter.getInstance().interpret<ArrayList<String>>("Safe Delete", requestOf(a)) { chain ->
        val entries = ArrayList<String>()
        chain.ask(answeringQuestion) { answered ->
          // Breaks the rule of the chain on purpose: the interpreter must not go on after the answers with the changed value.
          entries.add("after the answer")
          if (interruptedReads.getAndIncrement() == 0) waitForAWriteAction()
          answered.passValue(entries)
        }
      }?.value

    assertEquals(listOf("after the answer"), collectedEntries)
  }

  /** Requests an empty write action, which changes no PSI, and waits until it cancels the read action of the caller. */
  private fun waitForAWriteAction() {
    ApplicationManager.getApplication().invokeLater({ ApplicationManager.getApplication().runWriteAction {} }, ModalityState.any())
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
      ProgressManager.checkCanceled()
      Thread.sleep(10)
    }
    fail("No write action cancelled the read action")
  }

  /** The UI of the tests that asks the custom questions with their own [ModPlanQuestion.Custom.ask], and nothing else. */
  private class CustomAskingFlowUi : ModPlanTestFlowUi() {
    override fun ask(
      project: Project,
      title: String,
      question: ModPlanQuestion,
      initialValues: ModPlanOptionValues,
    ): ModPlanOptionValues? =
      if (question is ModPlanQuestion.Custom) question.ask(project, initialValues) else initialValues
  }

  /** A question whose dialog changes PSI, as a write while it is open does. */
  private inner class WritingQuestion(private val changedFile: PsiFile) : ModPlanQuestion.Custom {
    var askCount = 0

    override fun options(): List<ModPlanOption<*>> = listOf(readByProvider)

    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues {
      askCount++
      WriteCommandAction.runWriteCommandAction(project) {
        PsiDocumentManager.getInstance(project).getDocument(changedFile)!!.insertString(0, "x")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      return currentValues.with(readByProvider, true)
    }
  }

  fun `test isSupported needs an owner for every target`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    install(isApplicableTo = { it == a }) { _, _ -> nothingFound() }

    assertTrue(isSupported(a))
    assertFalse(isSupported(a, b))
  }

  fun `test a binary file is not supported and its plan is refused`() {
    val a = myFixture.addFileToProject("a.zip", "a")
    install { _, _ -> nothingFound() }

    assertFalse(isSupported(a))
    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusalOf(a).reason)
  }

  fun `test a target that is gone is refused without an element`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    install { _, _ -> nothingFound() }
    val request = requestOf(a)
    WriteCommandAction.runWriteCommandAction(project) { a.delete() }

    val refusedPlan = plan(request) as ModPlan.RefusedPlan

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusedPlan.reason)
    assertNull(refusedPlan.location)
    assertEquals(emptyList<ModPlanQuestion>(), askedQuestionsOf(request))
  }

  fun `test a request needs a target`() {
    assertThrows(IllegalArgumentException::class.java) {
      runReadActionBlocking {
        ModPlanRequest(project, emptyList(), ModPlanOptionValues.EMPTY)
      }
    }
  }
}
