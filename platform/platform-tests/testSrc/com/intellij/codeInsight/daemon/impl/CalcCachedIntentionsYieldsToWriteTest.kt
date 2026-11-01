// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainText
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Show Intention Actions (Alt+Enter) and the lazy quick fix wait must not hold a blocking read lock (IJPL-260316).
 *
 * An intention or a lazy quick fix spins until a write action posted meanwhile has run. The write must run quickly,
 * and only the interrupted phase must be computed again.
 */
@TestApplication
internal class CalcCachedIntentionsYieldsToWriteTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** The caret stays on the first line while highlighting, so the lazy fix on the second line is not computed yet. */
    private const val TEXT: String = "first line\nfoo bar"
    private const val PROBLEM: String = "bar"
    private const val PROBLEM_DESCRIPTION: String = "lazy problem"
    private const val INTENTION_TEXT: String = "Spinning test intention"
    private const val LAZY_FIX_TEXT: String = "Spinning test lazy fix"

    /** The spin gives up after this time, so the test fails on an assertion, not on the test timeout. */
    private val SPIN_BOUND = 3.seconds
    private val MAX_WRITE_WAIT = 1.seconds
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Test
  fun `Alt+Enter lets a write action run while an intention checks availability`() = timeoutRunBlocking(60.seconds) {
    val probe = WriteProbe()
    val setup = setUp(intentionProbe = probe, lazyFixProbe = null)

    probe.run(this) { calcCachedIntentions(setup) }

    probe.assertIndicatorWasSet()
    assertTrue(probe.intentionTexts().contains(INTENTION_TEXT), "intention must be offered: ${probe.intentionTexts()}")
    assertTrue(probe.intentionTexts().contains(LAZY_FIX_TEXT), "lazy fix must be offered: ${probe.intentionTexts()}")
    assertEquals(1, setup.lazyFixComputations.get(), "a write during the availability check must not compute the lazy fix again")
  }

  @Test
  fun `Alt+Enter lets a write action run while a lazy quick fix is computed`() = timeoutRunBlocking(60.seconds) {
    val probe = WriteProbe()
    val setup = setUp(intentionProbe = null, lazyFixProbe = probe)

    probe.run(this) { calcCachedIntentions(setup) }
    val availabilityChecksWithWrite = setup.availabilityChecks.getAndSet(0)

    probe.assertIndicatorWasSet()
    assertTrue(probe.intentionTexts().contains(INTENTION_TEXT), "intention must be offered: ${probe.intentionTexts()}")
    assertTrue(probe.intentionTexts().contains(LAZY_FIX_TEXT), "lazy fix must be offered: ${probe.intentionTexts()}")

    // the lazy fix is memoized now; the same call without a write gives the number of availability checks per call
    calcCachedIntentions(setup)
    assertEquals(setup.availabilityChecks.get(), availabilityChecksWithWrite,
                 "a write during the lazy fix wait must not repeat the availability checks")
  }

  @Test
  fun `Alt+Enter collects intentions again after a write removes text before the caret`() = timeoutRunBlocking(60.seconds) {
    val probe = WriteProbe()
    // the first check is in the collection, the second one is in the availability update of the collected intentions
    val setup = setUp(intentionProbe = probe, lazyFixProbe = null, intentionSpinsFromCheck = 2)
    val document = setup.editor.document
    val firstLine = TEXT.substringBefore('\n') + '\n'

    // the caret offset of the first collection is beyond the end of the shorter file
    probe.run(this, write = {
      CommandProcessor.getInstance().runUndoTransparentAction { document.deleteString(0, firstLine.length) }
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }) { calcCachedIntentions(setup) }

    assertTrue(probe.intentionTexts().contains(INTENTION_TEXT), "intention must be offered: ${probe.intentionTexts()}")
  }

  @Test
  fun `lazy quick fix wait in a read action lets a write action run`() = timeoutRunBlocking(60.seconds) {
    val probe = WriteProbe()
    val setup = setUp(intentionProbe = null, lazyFixProbe = probe)
    val info = setup.info

    probe.run(this) {
      withContext(Dispatchers.Default) {
        readAction {
          LazyQuickFixUpdater.getInstance(project).waitQuickFixesSynchronously(info, project, setup.editor.document)
        }
      }
      readAction {
        info.findRegisteredQuickFix { descriptor, _ -> descriptor.action.text.takeIf { it == LAZY_FIX_TEXT } }
      }?.let { listOf(it) } ?: emptyList()
    }

    assertEquals(listOf(LAZY_FIX_TEXT), probe.intentionTexts(), "lazy fix must be registered")
  }

  private class Setup(
    val editor: Editor,
    val file: PsiFile,
    val info: HighlightInfo,
    val lazyFixComputations: AtomicInteger,
    val availabilityChecks: AtomicInteger,
  )

  /**
   * Registers an intention and an annotator with a lazy fix on [PROBLEM], highlights the file, and moves the caret to
   * [PROBLEM]. The given probes are called from the intention's `isAvailable`, starting with its [intentionSpinsFromCheck]-th
   * call, and from the lazy fix computation.
   */
  private suspend fun setUp(intentionProbe: WriteProbe?, lazyFixProbe: WriteProbe?, intentionSpinsFromCheck: Int = 1): Setup {
    val disposable = codeInsightFixture.testRootDisposable
    val lazyFixComputations = AtomicInteger()
    val availabilityChecks = AtomicInteger()
    val armed = AtomicBoolean(false)

    val intention = object : AbstractIntentionAction() {
      override fun getText(): String = INTENTION_TEXT

      override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
        if (armed.get() && availabilityChecks.incrementAndGet() >= intentionSpinsFromCheck) {
          intentionProbe?.waitForWrite()
        }
        return true
      }

      override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {}
    }
    IntentionManager.getInstance().addAction(intention)
    Disposer.register(disposable) { IntentionManager.getInstance().unregisterIntention(intention) }

    val lazyFix = object : AbstractIntentionAction() {
      override fun getText(): String = LAZY_FIX_TEXT

      override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {}
    }
    LazyFixAnnotator.computeLazyFix = { registrar ->
      lazyFixComputations.incrementAndGet()
      lazyFixProbe?.waitForWrite()
      registrar.register(lazyFix)
    }
    Disposer.register(disposable) { LazyFixAnnotator.computeLazyFix = null }
    LanguageAnnotators.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, LazyFixAnnotator(), disposable)
    // the daemon must not compute the lazy fix in the background
    (LazyQuickFixUpdater.getInstance(project) as LazyQuickFixUpdaterImpl).stopUntil(disposable)

    codeInsightFixture.configureByText("test.txt", TEXT)
    val infos = withContext(Dispatchers.Default) { codeInsightFixture.doHighlighting(HighlightSeverity.ERROR) }
    val info = infos.find { it.description == PROBLEM_DESCRIPTION }
    assertNotNull(info, "annotator must highlight the problem: $infos")
    assertEquals(0, lazyFixComputations.get(), "lazy fix must not be computed before the test")

    withContext(Dispatchers.EDT) {
      codeInsightFixture.editor.caretModel.moveToOffset(TEXT.indexOf(PROBLEM) + 1)
    }
    armed.set(true)
    return Setup(codeInsightFixture.editor, codeInsightFixture.file, info!!, lazyFixComputations, availabilityChecks)
  }

  /** Marks [PROBLEM] as an error with a lazy fix. The daemon creates instances by reflection, so the fix lives in the companion. */
  private class LazyFixAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
      val compute = computeLazyFix ?: return
      if (element !is PsiPlainText) return
      val start = element.textRange.startOffset + element.text.indexOf(PROBLEM)
      holder.newAnnotation(HighlightSeverity.ERROR, PROBLEM_DESCRIPTION)
        .range(TextRange(start, start + PROBLEM.length))
        .withLazyQuickFix { registrar -> compute(registrar) }
        .create()
    }

    companion object {
      @Volatile
      var computeLazyFix: ((QuickFixActionRegistrar) -> Unit)? = null
    }
  }

  private suspend fun calcCachedIntentions(setup: Setup): List<String> {
    val intentions: CachedIntentions = withContext(Dispatchers.EDT) {
      ShowIntentionActionsHandler.calcCachedIntentions(project, setup.editor, setup.file)
    }
    return readAction { intentions.allActions.map { it.text } }
  }

  private class WriteProbe {
    private val started = CompletableDeferred<Unit>()
    private val writeDone = AtomicBoolean(false)
    private val attempts = AtomicInteger()
    private val cancellations = AtomicInteger()
    private val withoutIndicator = AtomicInteger()

    @Volatile
    private var postedAt = 0L

    @Volatile
    private var writtenAt = 0L

    @Volatile
    private var result: List<String> = emptyList()

    /** The modality of the code that calls the probe; the write is posted with it, so it is write-safe. */
    @Volatile
    private var modality: ModalityState = ModalityState.nonModal()

    fun intentionTexts(): List<String> = result

    /** Spins on `checkCanceled` until the write action has run, or [SPIN_BOUND] passes; counts every call. */
    fun waitForWrite() {
      attempts.incrementAndGet()
      if (ProgressManager.getGlobalProgressIndicator() == null) {
        withoutIndicator.incrementAndGet()
      }
      modality = ModalityState.defaultModalityState()
      started.complete(Unit)
      val deadline = System.nanoTime() + SPIN_BOUND.inWholeNanoseconds
      try {
        while (!writeDone.get() && System.nanoTime() < deadline) {
          ProgressManager.checkCanceled()
          Thread.sleep(1)
        }
      }
      catch (e: CancellationException) {
        cancellations.incrementAndGet()
        throw e
      }
    }

    /** Runs [compute], posts a write action that runs [write] once the probe spins, and checks the outcome. */
    suspend fun run(scope: CoroutineScope, write: () -> Unit = {}, compute: suspend () -> List<String>) {
      val errors = Collections.synchronizedList(ArrayList<String>())
      val errorProcessor = object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
          errors.add("$message: $t")
          return Action.ALL
        }
      }
      val writer = scope.launch(Dispatchers.Default) {
        started.await()
        postedAt = System.nanoTime()
        ApplicationManager.getApplication().invokeLater({
          ApplicationManager.getApplication().runWriteAction {
            write()
            writtenAt = System.nanoTime()
            writeDone.set(true)
          }
        }, modality)
      }
      LoggedErrorProcessor.executeWith(errorProcessor).use {
        result = compute()
        writer.join()
        withTimeout(10.seconds) {
          while (!writeDone.get()) delay(10)
        }
      }

      val writeWait = (writtenAt - postedAt).nanoseconds
      assertTrue(writeWait < MAX_WRITE_WAIT, "write action waited $writeWait for the probe (attempts: ${attempts.get()})")
      assertTrue(attempts.get() >= 2, "probe must be called again after the write, attempts: ${attempts.get()}")
      assertTrue(cancellations.get() >= 1, "probe must see a cancellation, cancellations: ${cancellations.get()}")
      assertEquals(emptyList<String>(), errors.toList(), "no errors must be logged")
    }

    fun assertIndicatorWasSet() {
      assertEquals(0, withoutIndicator.get(), "global progress indicator must be set inside the probe")
    }
  }
}
