// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.formatting.service.FormattingServiceSuppressor
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.formatting.service.structuredAsyncDocumentFormattingScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ChangedRangesInfo
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.FutureTask
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
class ReformatCodeProcessorFormattingServiceTest {
  companion object {
    @JvmStatic
    private fun reformatCalls(): List<Arguments> {
      val calls = listOf<Named<(CodeStyleManager, PsiFile) -> Unit>>(
        named("reformat(element)") { manager, file -> manager.reformat(file) },
        named("reformat(element, false)") { manager, file -> manager.reformat(file, false) },
        named("reformat(element, true)") { manager, file -> manager.reformat(file, true) },
        named("reformatRange(element, start, end)") { manager, file -> manager.reformatRange(file, 0, file.textLength) },
        named("reformatRange(element, start, end, false)") { manager, file -> manager.reformatRange(file, 0, file.textLength, false) },
        named("reformatRange(element, start, end, true)") { manager, file -> manager.reformatRange(file, 0, file.textLength, true) },
        named("reformatText(file, start, end)") { manager, file -> manager.reformatText(file, 0, file.textLength) },
        named("reformatText(file, ranges)") { manager, file -> manager.reformatText(file, listOf(file.textRange)) },
        named("reformatText(file, ranges, false)") { manager, file -> manager.reformatText(file, listOf(file.textRange), false) },
        named("reformatText(file, ranges, true)") { manager, file -> manager.reformatText(file, listOf(file.textRange), true) },
        named("reformatChanges(file, info)") { manager, file ->
          manager.reformatChanges(file, ChangedRangesInfo(listOf(file.textRange), null))
        },
      )
      return calls.flatMap { call ->
        listOf(false, true).flatMap { inWriteAction ->
          listOf(false, true).flatMap { forceSync ->
            listOf(false, true).map { structured -> arguments(call, inWriteAction, forceSync, structured) }
          }
        }
      }
    }
  }

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val fileInitialContent = "before"
  private val fileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("format.txt", fileInitialContent)

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `async formatting finishes before the next processor and notification`(
    forceSync: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val document = readAction { requireNotNull(file.viewProvider.document) }
    val beforeFormatText = "before formatting"
    writeCommandAction(file.project, "Change code") { document.setText(beforeFormatText) }

    var invoked = false
    val afterFormatText = "after"
    registerService(MockFormattingService(file) { request ->
      assertFalse(application.isWriteAccessAllowed)
      assertFalse(application.isReadAccessAllowed)
      assertEquals(listOf(TextRange(0, beforeFormatText.length)), request.formattingRanges)
      writeCommandAction(file.project, "Format code") { document.setText(afterFormatText) }
      invoked = true
    }, disposable)

    val processor = ReformatCodeProcessor(file, false)
    processor.setCollectInfo(true)

    var nextProcessorInvoked = false
    val nextProcessor = object : AbstractLayoutCodeProcessor(processor, "Next action", "Run next action") {
      override fun prepareTask(psiFile: PsiFile, processChangedTextOnly: Boolean): FutureTask<Boolean> = FutureTask {
        assertTrue(invoked)
        assertEquals(afterFormatText, document.text)
        assertEquals(CodeInsightBundle.message("hint.text.formatted.line", 1), processor.infoCollector?.reformatCodeNotification)
        nextProcessorInvoked = true
        true
      }
    }

    document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, forceSync)
    try {
      coroutineToIndicator { nextProcessor.processFilesUnderProgress(it) }
    }
    finally {
      document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, null)
    }

    assertTrue(invoked)
    assertTrue(nextProcessorInvoked)
    assertEquals(afterFormatText, readAction { document.text })
  }

  @ParameterizedTest(name = "{0}, inWriteAction={1}, forceSync={2}, structured={3}")
  @MethodSource("reformatCalls")
  fun `code style manager keeps async-only services asynchronous`(
    reformatAPIcall: (CodeStyleManager, PsiFile) -> Unit,
    inWriteAction: Boolean,
    forceSync: Boolean,
    structured: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val document = readAction { requireNotNull(file.viewProvider.document) }
    val finished = CompletableDeferred<Unit>()
    val afterFormatText = "after"

    val formattingService = MockFormattingService(file) { request ->
      assertFalse(application.isWriteAccessAllowed)
      assertFalse(application.isReadAccessAllowed)
      assertEquals(listOf(TextRange(0, fileInitialContent.length)), request.formattingRanges)
      writeCommandAction(file.project, "Format code") {
        document.setText(afterFormatText)
      }
      finished.complete(Unit)
    }
    registerService(formattingService, disposable)

    document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, forceSync)

    try {
      val reformatFile = {
        assertEquals(inWriteAction, application.isWriteAccessAllowed)
        reformatAPIcall(CodeStyleManager.getInstance(file.project), file)
      }

      val submit: suspend () -> Unit = {
        if (inWriteAction) {
          writeCommandAction(file.project, "Format code") {
            reformatFile()
          }
        }
        else {
          withContext(Dispatchers.EDT) {
            reformatFile()
          }
        }
      }

      if (structured) {
        structuredAsyncDocumentFormattingScope { submit() }
      }
      else {
        submit()
      }

      assertTrue(formattingService.taskCreated)
      finished.await()
    }
    finally {
      document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, null)
    }

    assertEquals(afterFormatText, readAction { document.text })
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `synchronous support does not restrict service selection`(
    supportsSynchronousFormatting: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val service = object : MockFormattingService(file) {
      override fun supportsSynchronousFormatting(): Boolean = supportsSynchronousFormatting
      override fun getFeatures(): Set<Feature> = setOf(Feature.AD_HOC_FORMATTING, Feature.FORMAT_FRAGMENTS, Feature.OPTIMIZE_IMPORTS)
      override fun canFormat(file: PsiFile, vararg features: Feature): Boolean = canFormat(file)
    }
    registerService(service, disposable)
    writeCommandAction(file.project, "Select formatter") {
      for (isExplicit in listOf(false, true)) {
        for (isCompleteFile in listOf(false, true)) {
          assertSame(service, FormattingServiceUtil.findService(file, isExplicit, isCompleteFile))
        }
      }
      assertSame(service, FormattingServiceUtil.findImportsOptimizingService(file))
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `ad hoc formatting requires declared support`(
    supportsAdHocFormatting: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val mockFormattingServiceWithAdHoc = object : MockFormattingService(file) {
      override fun getFeatures(): Set<Feature> {
        val features = super.getFeatures()
        return if (supportsAdHocFormatting) features + Feature.AD_HOC_FORMATTING else features
      }
    }
    val fallbackFormattingService = registerService(mockFormattingServiceWithAdHoc, disposable)
    readAction {
      assertSame(mockFormattingServiceWithAdHoc, FormattingServiceUtil.findService(file, true, true))
      val expectedService = if (supportsAdHocFormatting) mockFormattingServiceWithAdHoc else fallbackFormattingService
      assertSame(expectedService, FormattingServiceUtil.findService(file, false, true))
    }
  }

  @Test
  fun `service suppression applies to async formatting`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    var invoked = false
    val suppressedFormattingService = MockFormattingService(file) { invoked = true }
    val fallbackFormattingService = registerService(suppressedFormattingService, disposable)

    val suppressor = FormattingServiceSuppressor { candidateFile, candidateService ->
      candidateFile == file && candidateService == suppressedFormattingService
    }
    ExtensionTestUtil.maskExtensions(FormattingServiceSuppressor.EP_NAME, listOf(suppressor), disposable)

    readAction {
      assertSame(fallbackFormattingService, FormattingServiceUtil.findService(file, true, true))
    }

    coroutineToIndicator { ReformatCodeProcessor(file, false).processFilesUnderProgress(it) }

    assertFalse(invoked)
  }

  @Test
  fun `range formatting requires fragment support`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val formattingService = object : MockFormattingService(file) {
      override fun getFeatures(): Set<Feature> = emptySet()
    }
    val fallbackFormattingService = registerService(formattingService, disposable)
    readAction {
      assertSame(formattingService, FormattingServiceUtil.findService(file, true, true))
      assertSame(fallbackFormattingService, FormattingServiceUtil.findService(file, true, false))
    }
  }

  @Test
  fun `processor passes selected ranges to the async service`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val formattingRange = TextRange(1, 3)
    var invoked = false

    registerService(MockFormattingService(file) { request ->
      assertEquals(listOf(formattingRange), request.formattingRanges)
      invoked = true
    }, disposable)

    coroutineToIndicator { ReformatCodeProcessor(file, arrayOf(formattingRange)).processFilesUnderProgress(it) }
    assertTrue(invoked)
  }

  @Test
  fun `processor cancellation stops the formatting task`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val started = CompletableDeferred<Unit>()
    val stopped = CompletableDeferred<Unit>()
    registerService(MockFormattingService(file) {
      started.complete(Unit)
      try {
        awaitCancellation()
      }
      finally {
        stopped.complete(Unit)
      }
    }, disposable)

    val processor = launch {
      coroutineToIndicator { ReformatCodeProcessor(file, false).processFilesUnderProgress(it) }
    }

    started.await()
    processor.cancelAndJoin()
    assertTrue(stopped.isCompleted)
    assertEquals(fileInitialContent, readAction { file.viewProvider.document?.text })
  }

  @Test
  fun `processor completes after a task failure without waiting for a timeout`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = fileFixture.get()
    val failureException = IllegalStateException("Formatting failed")
    registerService(MockFormattingService(file) { throw failureException }, disposable)

    var reported = false
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if (t !is IllegalStateException || t.message != failureException.message) return super.processError(category, message, details, t)
        reported = true
        return Action.NONE
      }
    }

    LoggedErrorProcessor.executeWith(errorProcessor).use {
      assertTrue(coroutineToIndicator { ReformatCodeProcessor(file, false).processFilesUnderProgress(it) })
    }

    assertTrue(reported)
  }

  private fun registerService(formattingService: FormattingService, disposable: Disposable): FormattingService {
    val fallbackFormattingService = CoreFormattingService()
    ExtensionTestUtil.maskExtensions(FormattingService.EP_NAME,
                                     listOf(formattingService, fallbackFormattingService),
                                     disposable)
    return fallbackFormattingService
  }

  private open class MockFormattingService(
    private val file: PsiFile,
    private val format: suspend (AsyncFormattingRequest) -> Unit = { error("Unexpected formatting request") },
  ) : AsyncDocumentFormattingService() {
    var taskCreated: Boolean = false
      private set

    override fun getFeatures(): Set<Feature> = setOf(Feature.FORMAT_FRAGMENTS)
    override fun canFormat(file: PsiFile): Boolean = file == this.file
    override fun supportsSynchronousFormatting(): Boolean = false
    override fun getNotificationGroupId(): String = "Test formatting"
    override fun getName(): String = "Test formatter"
    override fun prepareForFormatting(document: Document, formattingContext: FormattingContext) {}
    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask {
      taskCreated = true
      return object : FormattingTask {
        override fun cancel(): Boolean = true
        override fun run() {
          runBlockingCancellable {
            format(request)
          }
          request.onTextReady(null)
        }
      }
    }
  }
}
