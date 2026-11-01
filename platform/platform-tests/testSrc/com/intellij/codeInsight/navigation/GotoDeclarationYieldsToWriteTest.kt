// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_TYPE_DECLARATION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_HIGHLIGHT_USAGES_IN_FILE
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.QueryExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Go to Declaration, Go to Type Declaration and Highlight Usages must not hold a blocking read lock while a provider waits (IJPL-260316).
 *
 * A provider spins until a write action posted meanwhile has run. The write must run quickly,
 * and the provider must be cancelled and called again.
 */
@TestApplication
internal class GotoDeclarationYieldsToWriteTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private const val TEXT: String = "foo b<caret>ar"
    private const val CARET_OFFSET: Int = 5

    /** The provider gives up after this time, so the test fails on an assertion, not on the test timeout. */
    private val SPIN_BOUND = 3.seconds
    private val MAX_WRITE_WAIT = 1.seconds

    /** Private in the platform (`declarations.kt`). */
    private val DECLARATION_PROVIDER_EP = ExtensionPointName<PsiSymbolDeclarationProvider>("com.intellij.psi.declarationProvider")
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Test
  fun `go to declaration lets a write action run while a handler waits`() = timeoutRunBlocking(30.seconds) {
    codeInsightFixture.configureByText("test.txt", TEXT)
    val probe = WriteProbe()
    val handler = GotoDeclarationHandler { sourceElement: PsiElement?, _: Int, _: Editor? ->
      probe.waitForWrite()
      sourceElement?.let { arrayOf(it) }
    }
    ExtensionTestUtil.maskExtensions(GotoDeclarationHandler.EP_NAME, listOf(handler), codeInsightFixture.testRootDisposable)

    probe.run(this, ACTION_GOTO_DECLARATION)
  }

  @Test
  fun `go to type declaration lets a write action run while a provider waits`() = timeoutRunBlocking(30.seconds) {
    codeInsightFixture.configureByText("test.txt", TEXT)
    val probe = WriteProbe()
    val withoutAlternativeResolve = AtomicInteger()
    val provider = TypeDeclarationProvider { symbol ->
      if (!DumbService.getInstance(symbol.project).isAlternativeResolveEnabled) {
        withoutAlternativeResolve.incrementAndGet()
      }
      probe.waitForWrite()
      arrayOf(symbol)
    }
    val disposable = codeInsightFixture.testRootDisposable
    ExtensionTestUtil.maskExtensions(DECLARATION_PROVIDER_EP, listOf(PlainTextDeclarationProvider()), disposable)
    ExtensionTestUtil.maskExtensions(TypeDeclarationProvider.EP_NAME, listOf(provider), disposable)

    // alternative resolve is enabled only in dumb mode
    val dumbTask = withContext(Dispatchers.Default) { DumbModeTestUtils.startEternalDumbModeTask(project) }
    try {
      probe.run(this, ACTION_GOTO_TYPE_DECLARATION)
    }
    finally {
      withContext(Dispatchers.Default) { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, dumbTask) }
    }

    assertEquals(0, withoutAlternativeResolve.get(), "alternative resolve must be enabled inside the provider")
  }

  @Test
  fun `go to type declaration navigates after a write action reparses the target`() = timeoutRunBlocking(30.seconds) {
    codeInsightFixture.configureByText("test.txt", TEXT)
    val probe = WriteProbe()
    val provider = TypeDeclarationProvider { symbol -> arrayOf(SpinningNavigationElement(symbol, probe)) }
    val disposable = codeInsightFixture.testRootDisposable
    ExtensionTestUtil.maskExtensions(DECLARATION_PROVIDER_EP, listOf(PlainTextDeclarationProvider()), disposable)
    ExtensionTestUtil.maskExtensions(TypeDeclarationProvider.EP_NAME, listOf(provider), disposable)

    val document = codeInsightFixture.editor.document
    // the whole plain-text leaf is reparsed; the text is added after the caret, so only navigation moves the caret
    probe.run(this, ACTION_GOTO_TYPE_DECLARATION) {
      document.insertString(document.textLength, "_x")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  @Test
  fun `highlight usages restores the symbol after a write action reparses the file`() = timeoutRunBlocking(30.seconds) {
    codeInsightFixture.configureByText("test.txt", TEXT)
    val probe = WriteProbe()
    val invalidElements = AtomicInteger()
    val searchedTexts = Collections.synchronizedList(ArrayList<String>())
    val searcher = QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> { parameters, _ ->
      val element = parameters.elementToSearch
      if (element.isValid) searchedTexts.add(element.text) else invalidElements.incrementAndGet()
      probe.waitForWrite()
      true
    }
    val disposable = codeInsightFixture.testRootDisposable
    ExtensionTestUtil.maskExtensions(DECLARATION_PROVIDER_EP, listOf(PlainTextDeclarationProvider()), disposable)
    ExtensionTestUtil.maskExtensions(ReferencesSearch.EP_NAME, listOf(searcher), disposable)

    val document = codeInsightFixture.editor.document
    val changedText = "foo_x bar"
    // the whole plain-text leaf is reparsed, so the symbol captured before the modal progress holds an invalid element
    probe.run(this, ACTION_HIGHLIGHT_USAGES_IN_FILE, navigates = false) {
      document.insertString(3, "_x")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    assertEquals(0, invalidElements.get(), "the symbol must be restored before the search")
    assertEquals(changedText, searchedTexts.last(), "the restored symbol must point at the reparsed element: $searchedTexts")
  }

  /** Makes the plain-text leaf a declaration, so Go to Type Declaration asks [TypeDeclarationProvider] for it. */
  private class PlainTextDeclarationProvider : PsiSymbolDeclarationProvider {
    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
      if (element !is PsiPlainText) return emptyList()
      return listOf(object : PsiSymbolDeclaration {
        override fun getDeclaringElement(): PsiElement = element
        override fun getRangeInDeclaringElement(): TextRange = TextRange(0, element.textLength)
        override fun getSymbol(): Symbol = PsiSymbolService.getInstance().asSymbol(element)
      })
    }
  }

  /**
   * A type declaration that spins in [navigationRequest] and then navigates to [target].
   * Like PSI navigation of the platform, it gives no request once [target] is invalid.
   */
  private class SpinningNavigationElement(private val target: PsiElement, private val probe: WriteProbe) : FakePsiElement() {
    override fun getParent(): PsiElement = target

    override fun isValid(): Boolean = target.isValid

    override fun navigationRequest(): NavigationRequest? {
      probe.waitForWrite()
      return if (target.isValid) PsiNavigationSupport.getInstance().getDescriptor(target)?.navigationRequest() else null
    }
  }

  private inner class WriteProbe {
    private val started = CompletableDeferred<Unit>()
    private val writeDone = AtomicBoolean(false)
    private val attempts = AtomicInteger()
    private val cancellations = AtomicInteger()
    private val withoutIndicator = AtomicInteger()

    @Volatile
    private var postedAt = 0L

    @Volatile
    private var writtenAt = 0L

    /** The modality of the modal progress the provider runs under; the write is posted with it, so it is write-safe. */
    @Volatile
    private var modality: ModalityState = ModalityState.nonModal()

    /** Called by the provider: spins on `checkCanceled` until the write action has run, or [SPIN_BOUND] passes. */
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

    /**
     * Runs [actionId] in the editor, posts a write action once the provider waits, and checks the outcome.
     * [write] runs inside the write action; with [navigates] the caret must move to the target afterwards.
     */
    suspend fun run(scope: CoroutineScope, actionId: String, navigates: Boolean = true, write: () -> Unit = {}) {
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
        withContext(Dispatchers.EDT) {
          codeInsightFixture.performEditorAction(actionId)
        }
        writer.join()
        withTimeout(10.seconds) {
          while (!writeDone.get()) delay(10)
        }
        if (navigates) {
          // navigation runs asynchronously after the action
          withTimeout(10.seconds) {
            while (withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset } == CARET_OFFSET) delay(10)
          }
        }
      }

      val writeWait = (writtenAt - postedAt).nanoseconds
      assertTrue(writeWait < MAX_WRITE_WAIT, "write action waited $writeWait for the provider (attempts: ${attempts.get()})")
      assertTrue(attempts.get() >= 2, "provider must be called again after the write, attempts: ${attempts.get()}")
      assertTrue(cancellations.get() >= 1, "provider must see a cancellation, cancellations: ${cancellations.get()}")
      assertEquals(0, withoutIndicator.get(), "global progress indicator must be set inside the provider")
      if (navigates) {
        assertEquals(0, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }, "caret must move to the target")
      }
      assertEquals(emptyList<String>(), errors.toList(), "no errors must be logged")
    }
  }
}
