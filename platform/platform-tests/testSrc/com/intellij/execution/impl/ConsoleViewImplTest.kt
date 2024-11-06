// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.concurrency.JobScheduler
import com.intellij.execution.ConsoleFolding
import com.intellij.execution.filters.*
import com.intellij.execution.process.AnsiEscapeDecoderTest
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.*
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import com.intellij.util.LineSeparator
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UIUtil
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.min

class ConsoleViewImplTest : LightPlatformTestCase() {
  private lateinit var console: ConsoleViewImpl

  private val consoleEditor
    get() = console.editor!! as EditorEx

  public override fun setUp() {
    super.setUp()
    console = createConsole()
  }

  public override fun tearDown() {
    try {
      Disposer.dispose(console)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testTypeText() {
    console.print("Initial", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    console.clear()
    console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT)
    console.waitAllRequests()
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(2, console.contentSize)
  }

  fun testTypeBeforeSelectionMustNotLeadToInvalidOffset() {
    console.print("Initial", ConsoleViewContentType.USER_INPUT)
    console.flushDeferredText()
    console.clear()
    console.print("Hi", ConsoleViewContentType.USER_INPUT)
    console.waitAllRequests()
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(2, console.contentSize)
    assertEquals(2, consoleEditor.caretModel.offset)
    consoleEditor.caretModel.setCaretsAndSelections(mutableListOf(CaretState(LogicalPosition(0, 0),
                                                                             LogicalPosition(0, 0),
                                                                             LogicalPosition(0, 2))))
    assertEquals(0, consoleEditor.caretModel.offset)
    typeIn(consoleEditor, 'x')
    console.waitAllRequests()
    UIUtil.dispatchAllInvocationEvents()
    assertEquals(1, console.contentSize)
  }

  fun testConsolePrintsSomethingAfterDoubleClear() {
    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, testRootDisposable)
    val latch = CountDownLatch(1)
    alarm.addRequest(Runnable {
      console.clear()
      console.clear()
      console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT)
      latch.countDown()
    }, 0)
    latch.await()
    while (console.hasDeferredOutput()) {
      UIUtil.dispatchAllInvocationEvents()
      TimeoutUtil.sleep(5)
    }
    assertEquals("Test", console.text)
  }

  fun testConsolePrintsSomethingAfterClearPrintScroll() {
    val alarm = Alarm(testRootDisposable)
    repeat(1000/*000*/) {
      val latch = CountDownLatch(1)
      alarm.addRequest(Runnable {
        console.clear()
        console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT)
        console.scrollTo(0)
        latch.countDown()
      }, 0)
      while (latch.count != 0L) {
        UIUtil.dispatchAllInvocationEvents()
      }
      while (console.hasDeferredOutput()) {
        UIUtil.dispatchAllInvocationEvents()
      }
      assertEquals("Test", console.text)
    }
  }

  fun testClearAndPrintWhileAnotherClearExecution() {
    ThreadingAssertions.assertEventDispatchThread()
    repeat(100) { i ->
      // To speed up test execution, set -Dconsole.flush.delay.ms=5 to reduce ConsoleViewImpl.DEFAULT_FLUSH_DELAY
      //System.out.println("Attempt #" + i);
      console.clear() // 1-st clear
      val latch = CountDownLatch(1)
      val future = ApplicationManager.getApplication().executeOnPooledThread(Runnable {
        console.clear() // 2-nd clear
        console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT)
        latch.countDown()
      })
      UIUtil.dispatchAllInvocationEvents() // flush 1-st clear request
      assertTrue(latch.await(30, TimeUnit.SECONDS))
      UIUtil.dispatchAllInvocationEvents() // flush 2-nd clear request
      while (console.hasDeferredOutput()) {
        UIUtil.dispatchAllInvocationEvents()
        TimeoutUtil.sleep(1)
      }
      assertEquals("iteration $i", "Test", console.text)
      future.get()
    }
  }

  fun testTypeInEmptyConsole() {
    console.clear()
    console.waitAllRequests()
    EditorActionManager.getInstance()
    val dataContext = DataManager.getInstance().getDataContext(console.component)
    val action = TypedAction.getInstance()
    action.actionPerformed(consoleEditor, 'h', dataContext)
    assertEquals(1, console.contentSize)
  }

  fun testTypingAfterMultipleCR() {
    val typedAction = TypedAction.getInstance()

    val dataContext = consoleEditor.dataContext
    console.print("System output\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    console.print("\r\r\r\r\r\r\r", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    typedAction.actionPerformed(consoleEditor, '1', dataContext)
    typedAction.actionPerformed(consoleEditor, '2', dataContext)

    assertEquals("System output\n12", consoleEditor.document.text)
  }

  fun testCRLF() {
    console.clear()
    console.print("Hello\r", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("\nWorld", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertEquals("Hello\nWorld", console.text)
  }

  fun testCRTypeCR() {
    console.print("\rHi\rMr\rSmith", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertEquals("Smith", console.text)
  }

  fun testCRTypeTearCR() {
    console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT)

    console.print("\rMr\rSmith", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertEquals("Smith", console.text)
  }

  fun testCRTypeFlushCR() {
    console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertEquals("Hi", console.text)
    console.print("\rMr\rSmith", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertEquals("Smith", console.text)
  }

  fun testCaretAfterMultilineOutput() {
    assertCaretAt(0, 0)
    console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertCaretAt(0, 2)
    console.print("\nprompt:", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    assertCaretAt(1, 7)
  }

  private fun assertCaretAt(line: Int, column: Int) {
    val position = consoleEditor.caretModel.logicalPosition
    assertEquals(line, position.line)
    assertEquals(column, position.column)
  }

  private fun createConsole(): ConsoleViewImpl {
    return createConsole(false, project)
  }

  fun testDoNotRemoveEverythingWhenOneCharIsPrintedAfterLargeText() {
    withCycleConsoleNoFolding(1) { console: ConsoleViewImpl ->
      console.print(StringUtil.repeat("a", 5000), ConsoleViewContentType.NORMAL_OUTPUT)
      console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
      console.waitAllRequests()
      assertEquals(StringUtil.repeat("a", 1023) + "\n", console.text)
    }
  }

  fun testPerformance() {
    withCycleConsoleNoFolding(100) { console: ConsoleViewImpl ->
      Benchmark.newBenchmark("console print") {
        console.clear()
        repeat(10_000_000) {
          console.print("xxx\n", ConsoleViewContentType.NORMAL_OUTPUT)
          console.print("yyy\n", ConsoleViewContentType.SYSTEM_OUTPUT)
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }
        LightPlatformCodeInsightTestCase.type('\n', consoleEditor, project)
        console.waitAllRequests()
      }.start()
    }
  }

  fun testLargeConsolePerformance() {
    withCycleConsoleNoFolding(UISettings.getInstance().consoleCycleBufferSizeKb) { console: ConsoleViewImpl ->
      Benchmark.newBenchmark("console print") {
        console.clear()
        repeat(20_000_000) {
          console.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        console.waitAllRequests()
      }.start()
    }
  }

  fun testPerformanceOfMergeableTokens() {
    withCycleConsoleNoFolding(1000) { console: ConsoleViewImpl ->
      Benchmark.newBenchmark("console print with mergeable tokens") {
        console.clear()
        repeat(10_000_000) {
          console.print("xxx\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }
        UIUtil.dispatchAllInvocationEvents()
        console.waitAllRequests()
        val model = DocumentMarkupModel.forDocument(console.editor!!.document, project, true)
        val highlighter = assertOneElement(model.allHighlighters)
        assertEquals(TextRange(0, console.editor!!.document.textLength), highlighter.textRange)
      }.start()
    }
  }

  private fun withCycleConsoleNoFolding(capacityKB: Int, runnable: Consumer<in ConsoleViewImpl>) {
    val uiSettings = UISettings.getInstance()
    val oldUse = uiSettings.overrideConsoleCycleBufferSize
    val oldSize = uiSettings.consoleCycleBufferSizeKb

    uiSettings.overrideConsoleCycleBufferSize = true
    uiSettings.consoleCycleBufferSizeKb = capacityKB
    // create new to reflect changed buffer size
    val console = createConsole(true, project)

    val point = ConsoleFolding.EP_NAME.point
    (point as ExtensionPointImpl<ConsoleFolding>).maskAll(mutableListOf(), console, false)
    assertEmpty(point.extensions)

    try {
      runnable.consume(console)
    }
    finally {
      Disposer.dispose(console)
      uiSettings.overrideConsoleCycleBufferSize = oldUse
      uiSettings.consoleCycleBufferSizeKb = oldSize
    }
  }

  fun testBigOutputDoesntMemoryOverflow() {
    withCycleConsoleNoFolding(100) { console: ConsoleViewImpl ->
      repeat(10000000) { i ->
        console.print("---- $i----", ConsoleViewContentType.NORMAL_OUTPUT)
      }
    }
  }

  fun testCanPrintUserInputFromBackground() {
    val future = JobScheduler.getScheduler().submit(Runnable { console.print("input", ConsoleViewContentType.USER_INPUT) })

    while (!future.isDone) {
      UIUtil.dispatchAllInvocationEvents()
    }
    future.get()
  }

  fun testUserInputIsSentToProcessAfterNewLinePressed() {
    val testProcess = AnsiEscapeDecoderTest.createTestProcess()
    val outputStream = testProcess.outputStream as ByteArrayOutputStream

    AnsiEscapeDecoderTest.withProcessHandlerFrom(testProcess) { handler: ProcessHandler ->
      withCycleConsoleNoFolding(100) { console: ConsoleViewImpl ->
        console.attachToProcess(handler)
        outputStream.reset()
        console.print("I", ConsoleViewContentType.USER_INPUT)
        console.waitAllRequests()
        assertEquals(0, outputStream.size())
        console.print("K", ConsoleViewContentType.USER_INPUT)
        console.waitAllRequests()
        assertEquals(0, outputStream.size())
        console.print("\n", ConsoleViewContentType.USER_INPUT)
        console.waitAllRequests()
        assertEquals("IK\n", outputStream.toString())
      }
    }
  }

  fun testUserTypingIsSentToProcessAfterNewLinePressed() {
    val testProcess = AnsiEscapeDecoderTest.createTestProcess()
    val outputStream = testProcess.outputStream as ByteArrayOutputStream

    AnsiEscapeDecoderTest.withProcessHandlerFrom(testProcess) { handler: ProcessHandler ->
      withCycleConsoleNoFolding(100) { console: ConsoleViewImpl ->
        console.attachToProcess(handler)
        outputStream.reset()
        val editor = console.editor!!

        typeIn(editor, 'X')
        console.waitAllRequests()
        assertEquals(0, outputStream.size())

        typeIn(editor, 'Y')
        console.waitAllRequests()
        assertEquals(0, outputStream.size())

        typeIn(editor, '\n')
        console.waitAllRequests()
        assertEquals(3, outputStream.size())
        assertEquals("XY\n", outputStream.toString())
      }
    }
  }


  fun testCompleteLinesWhenMessagesArePrintedConcurrently() {
    assertCompleteLines("stdout ", 20000, 2, "stderr ", 20000, 3, 10)
    assertCompleteLines("stdout ", 20000, 5, "stderr ", 20000, 7, 10)
    assertCompleteLines("info: ", 20000, 11, "error: ", 20000, 13, 10)
    assertCompleteLines("Hello", 40000, 199, "Bye", 40000, 101, 5)
  }

  private fun assertCompleteLines(
    stdoutLinePrefix: String, stdoutLines: Int, stdoutBufferSize: Int,
    stderrLinePrefix: String, stderrLines: Int, stderrBufferSize: Int,
    rerunCount: Int
  ) {
    val processHandler: ProcessHandler = NopProcessHandler()
    console.attachToProcess(processHandler)
    repeat(rerunCount) {
      console.clear()
      console.waitAllRequests()
      val estimatedPrintedChars = stdoutLines * (stdoutLinePrefix.length + stdoutLines.toString().length + 1) +
                                  stderrLines * (stderrLinePrefix.length + stderrLines.toString().length + 1)
      assertTrue(ConsoleBuffer.getCycleBufferSize() > estimatedPrintedChars)
      val stdout = sendMessagesInBackground(processHandler, stdoutLinePrefix, stdoutLines, ProcessOutputType.STDOUT,
                                                       stdoutBufferSize)
      val stderr = sendMessagesInBackground(processHandler, stderrLinePrefix, stderrLines, ProcessOutputType.STDERR,
                                                       stderrBufferSize)
      stdout.get()
      stderr.get()
      (console.state as ConsoleViewRunningState).streamsSynchronizer!!.waitForAllFlushed()
      console.flushDeferredText()
      val text = consoleEditor.document.text
      val lines = StringUtil.splitByLinesKeepSeparators(text)
      var readStdoutLines = 0
      var readStderrLines = 0
      for (line in lines) {
        if (line.startsWith(stdoutLinePrefix)) {
          assertEquals(stdoutLinePrefix + (readStdoutLines + 1) + LineSeparator.LF.separatorString, line)
          readStdoutLines++
        }
        else {
          assertEquals(stderrLinePrefix + (readStderrLines + 1) + LineSeparator.LF.separatorString, line)
          readStderrLines++
        }
      }
      assertEquals(stdoutLines, readStdoutLines)
      assertEquals(stderrLines, readStderrLines)
    }
  }

  private fun sendMessagesInBackground(
    processHandler: ProcessHandler,
    linePrefix: String,
    lineCount: Int,
    outputType: ProcessOutputType,
    bufferSize: Int
  ): Future<*> {
    return ApplicationManager.getApplication().executeOnPooledThread(Runnable {
      var bufferRestSize = bufferSize
      for (i in 1..lineCount) {
        val text = linePrefix + i + LineSeparator.LF.separatorString
        var printedTextSize = 0
        while (printedTextSize < text.length) {
          if (bufferRestSize == 0) {
            bufferRestSize = bufferSize
          }
          val endInd = min((printedTextSize + bufferRestSize).toDouble(), text.length.toDouble()).toInt()
          val textToPrint = text.substring(printedTextSize, endInd)
          processHandler.notifyTextAvailable(textToPrint, outputType)
          bufferRestSize -= textToPrint.length
          printedTextSize += textToPrint.length
        }
      }
    })
  }

  fun testBackspaceDoesDeleteTheLastTypedChar() {
    console.print("xxxx", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a", ConsoleViewContentType.USER_INPUT)
    console.print("b", ConsoleViewContentType.USER_INPUT)
    console.print("c", ConsoleViewContentType.USER_INPUT)
    console.print("d", ConsoleViewContentType.USER_INPUT)
    console.flushDeferredText()
    assertEquals("xxxxabcd", consoleEditor.document.text)

    backspace()
    assertEquals("xxxxabc", consoleEditor.document.text)
    backspace()
    assertEquals("xxxxab", consoleEditor.document.text)
    backspace()
    assertEquals("xxxxa", consoleEditor.document.text)
    backspace()
    assertEquals("xxxx", consoleEditor.document.text)
  }

  private fun backspace() {
    val handler = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    CommandProcessor.getInstance().executeCommand(project,
                                                  Runnable { EditorTestUtil.executeAction(consoleEditor, true, handler) },
                                                  "", null, consoleEditor.document)
  }

  fun testCRPrintCR() {
    repeat(25) { i ->
      console.print("\r" + i, ConsoleViewContentType.NORMAL_OUTPUT)
      Thread.sleep(100)
    }
    console.flushDeferredText()
    console.waitAllRequests()
    assertEquals("24", console.text)
  }

  fun testInputFilter() {
    Disposer.dispose(console) // have to re-init extensions
    val registered = mutableListOf<Pair<String?, ConsoleViewContentType?>?>()
    val crazyProvider = ConsoleInputFilterProvider { project: Project? ->
      arrayOf(InputFilter { text: String?, contentType: ConsoleViewContentType? ->
        registered.add(Pair(text, contentType))
        listOf(Pair("+!$text-!", contentType))
      })
    }
    ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS.point.registerExtension(crazyProvider, testRootDisposable)
    console = createConsole()
    val expectedText = StringBuilder()
    val expectedRegisteredTokens = mutableListOf<Pair<String?, ConsoleViewContentType?>?>()
    repeat(25) { i ->
      val chunk = i.toString()

      console.print(chunk, ConsoleViewContentType.USER_INPUT)
      expectedText.append("+!$i-!")
      expectedRegisteredTokens.add(Pair(chunk, ConsoleViewContentType.USER_INPUT))

      chunk.forEach { c ->
        typeIn(consoleEditor, c)
        expectedText.append("+!$c-!")
        expectedRegisteredTokens.add(
          Pair(c.toString(), ConsoleViewContentType.USER_INPUT))
      }
    }
    console.flushDeferredText()
    console.waitAllRequests()
    assertEquals(expectedText.toString(), console.text)
    assertEquals(expectedRegisteredTokens, registered)
  }

  fun testConsoleDependentInputFilter() {
    Disposer.dispose(console) // have to re-init extensions
    val filterProvider: ConsoleDependentInputFilterProvider = object : ConsoleDependentInputFilterProvider() {
      override fun getDefaultFilters(
        consoleView: ConsoleView,
        project: Project,
        scope: GlobalSearchScope
      ): MutableList<InputFilter?> {
        return mutableListOf(InputFilter { text: String?, contentType: ConsoleViewContentType? ->
          mutableListOf(Pair("!$text!", contentType))
        })
      }
    }
    maskExtensions(
      ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS,
      listOf(filterProvider),
      testRootDisposable)

    console = createConsole(true, project)
    console.print("Foo", ConsoleViewContentType.USER_INPUT)
    console.print("Bar", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("Baz", ConsoleViewContentType.ERROR_OUTPUT)
    console.flushDeferredText()
    console.waitAllRequests()
    assertEquals("!Foo!!Bar!!Baz!", console.text)
  }

  fun testCustomFiltersPrecedence() {
    val predefinedHyperlink = HyperlinkInfo { project: Project? -> }
    val predefinedFilter = Filter { line: String?, entireLength: Int -> Filter.Result(0, 1, predefinedHyperlink) }
    val customHyperlink = HyperlinkInfo { project: Project? -> }
    val customFilter = Filter { line: String?, entireLength: Int -> Filter.Result(0, 10, customHyperlink) }

    Disposer.dispose(console) // have to re-init extensions

    val predefinedProvider = ConsoleFilterProvider { project: Project? -> arrayOf(predefinedFilter) }
    maskExtensions(
      ConsoleFilterProvider.FILTER_PROVIDERS,
      listOf(predefinedProvider),
      testRootDisposable)

    console = createConsole(true, project)
    console.addMessageFilter(customFilter)
    console.print("foo bar buz test", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()
    console.waitAllRequests()

    val hyperlinks = console.getHyperlinks()!!
    assertNotNull(hyperlinks.getHyperlinkAt(0))
    assertEquals(customHyperlink, hyperlinks.getHyperlinkAt(0))
    assertNotNull(hyperlinks.getHyperlinkAt(10))
    assertEquals(customHyperlink, hyperlinks.getHyperlinkAt(10))
  }

  fun testSeveralUpdatesDoNotProduceDuplicateHyperlinks() {
    val customHyperlink = HyperlinkInfo { project: Project? -> }
    val customFilter = Filter { line: String?, entireLength: Int ->
      TimeUnit.MILLISECONDS.sleep(100)
      Filter.Result(0, 10, customHyperlink)
    }
    console.addMessageFilter(customFilter)
    console.print("the only line", ConsoleViewContentType.NORMAL_OUTPUT)
    console.waitAllRequests()
    repeat(10) {
      console.rehighlightHyperlinksAndFoldings()
    }
    console.waitAllRequests()
    val hyperlinks = console.getHyperlinks()!!
    hyperlinks.waitForPendingFilters(2000)
    assertEquals(1, hyperlinks.findAllHyperlinksOnLine(0).size)
  }


  fun testExpirableTokenProvider() {
    val tokenProvider = ExpirableTokenProvider()
    val token1 = tokenProvider.createExpirable()

    assertFalse(token1.isExpired())

    tokenProvider.invalidateAll()
    assertTrue(token1.isExpired())

    val token2 = tokenProvider.createExpirable()
    assertFalse(token2.isExpired())
  }

  fun testNotHighlightedWhenExpired() {
    val customHyperlink = HyperlinkInfo { project: Project? -> }
    val customFilter = Filter { line: String?, entireLength: Int ->
      TimeUnit.MILLISECONDS.sleep(500)
      Filter.Result(0, 10, customHyperlink)
    }
    console.addMessageFilter(customFilter)
    console.print("the only line", ConsoleViewContentType.NORMAL_OUTPUT)
    console.waitAllRequests()
    console.getHyperlinks()!!.waitForPendingFilters(2000)
    assertEquals(1, console.getHyperlinks()!!.findAllHyperlinksOnLine(0).size)

    // ^-- not highlighted when expired
    console.rehighlightHyperlinksAndFoldings()
    console.invalidateFiltersExpirableTokens()
    console.getHyperlinks()!!.waitForPendingFilters(2000)
    assertEquals(0, console.getHyperlinks()!!.findAllHyperlinksOnLine(0).size)
    // ^-- highlighted when no expiration
  }

  fun testBackspaceDeletesPreviousOutput() {
    assertPrintedText(arrayOf("Test", "\b"), "Tes")
    assertPrintedText(arrayOf("Test", "\b", "\b"), "Te")
    assertPrintedText(arrayOf("Hello", "\b\b\b\b", "allo"), "Hallo")
    assertPrintedText(arrayOf("A\b\b\bha\bop", "\bul\bpp", "\b\bsl\be"), "house")
    assertPrintedText(arrayOf("\b\bTest\b\b\b\b\b", "Done", "\b\b\b"), "D")
    assertPrintedText(arrayOf("\b\b\b\b\b\b\b"), "")
    assertPrintedText(arrayOf("The\b\b\b\b", "first lint", "\be\n",
                                      "\b\b\bsecond lone", "\b\b\bine\n",
                                      "\bthird\b\b\b\b\b\b\b\bthe third line"), "first line\nsecond line\nthe third line")
    assertPrintedText(arrayOf("\n\n\b\bStart\nEnq\bd"), "\n\nStart\nEnd")
    assertPrintedText(arrayOf("\nEnter your pass:", "\rsecreq\bt"), "\nsecret")
    assertPrintedText(arrayOf("test\b\b\b\b\b\bline1\n\blinee\b2\r\n\blin\b\b\b\bline?", "\b3\n", "Done\n"),
                      "line1\nline2\nline3\nDone\n")
  }

  private fun assertPrintedText(textToPrint: Array<String>, expectedText: String) {
    console.clear()
    console.waitAllRequests()
    assertEquals("", console.text)
    for (text in textToPrint) {
      console.print(text, ConsoleViewContentType.NORMAL_OUTPUT)
    }
    console.flushDeferredText()
    assertEquals(expectedText, console.text)

    console.clear()
    console.waitAllRequests()
    assertEquals("", console.text)
    for (text in textToPrint) {
      console.print(text, ConsoleViewContentType.NORMAL_OUTPUT)
      console.flushDeferredText()
    }
    assertEquals(expectedText, console.text)
  }

  fun testBackspacePerformance() {
    val nCopies = 10000
    val `in` = StringUtil.repeat("\na\nb\bc", nCopies)
    Benchmark.newBenchmark("print newlines with backspace") {
      repeat(2) {
        console.clear()
        val printCount = ConsoleBuffer.getCycleBufferSize() / `in`.length
        repeat(printCount) {
          console.print(`in`, ConsoleViewContentType.NORMAL_OUTPUT)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        console.waitAllRequests()
        assertEquals(printCount.toLong() * nCopies * "\na\nc".length, console.contentSize.toLong())
      }
    }.start()
  }

  fun testBackspaceChangesHighlightingRanges1() {
    console.print("Starting\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("Hello", ConsoleViewContentType.ERROR_OUTPUT)
    console.print("\b\b\b\bDone", ConsoleViewContentType.SYSTEM_OUTPUT)
    console.flushDeferredText()
    assertEquals("Starting\nHDone", console.text)

    val actualHighlighters = allRangeHighlighters
    assertMarkersEqual(actualHighlighters,
                       ExpectedHighlighter(0, 9, ConsoleViewContentType.NORMAL_OUTPUT),
                       ExpectedHighlighter(9, 10, ConsoleViewContentType.ERROR_OUTPUT),
                       ExpectedHighlighter(10, 14, ConsoleViewContentType.SYSTEM_OUTPUT)
    )
  }

  fun testBackspaceChangesHighlightingRanges2() {
    console.print("Ready\n\bSet\b\b\b\b\bSteady\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("\b\b\bGo", ConsoleViewContentType.ERROR_OUTPUT)
    console.print("token1", ConsoleViewContentType.SYSTEM_OUTPUT)
    console.print("\b\b\b\btoken2\bX\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("temp", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("\b\b\b\b\b\b\b\b\b", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("_", ConsoleViewContentType.SYSTEM_OUTPUT)
    console.print("Done", ConsoleViewContentType.SYSTEM_OUTPUT)
    console.flushDeferredText()

    assertEquals("Ready\nSteady\nGototokenX\n_Done", console.text)
    assertMarkersEqual(allRangeHighlighters,
                       ExpectedHighlighter(0, 13, ConsoleViewContentType.NORMAL_OUTPUT),  // Ready\nSteady\n
                       ExpectedHighlighter(13, 15, ConsoleViewContentType.ERROR_OUTPUT),  // Go
                       ExpectedHighlighter(15, 17, ConsoleViewContentType.SYSTEM_OUTPUT),  // to
                       ExpectedHighlighter(17, 24, ConsoleViewContentType.NORMAL_OUTPUT),  // tokenX\n
                       ExpectedHighlighter(24, 29, ConsoleViewContentType.SYSTEM_OUTPUT) // Done
    )
  }

  fun testBackspaceChangesHighlightingRanges3() {
    console.print("Test1\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("Test2", ConsoleViewContentType.SYSTEM_OUTPUT)
    console.print("\b\b\b\b\b\b", ConsoleViewContentType.ERROR_OUTPUT)
    console.flushDeferredText()

    assertEquals("Test1\n", console.text)
    assertMarkersEqual(allRangeHighlighters,
                       ExpectedHighlighter(0, 6, ConsoleViewContentType.NORMAL_OUTPUT) // Test1\n
    )
  }

  fun testSubsequentFoldsAreCombined() {
    ApplicationManager.getApplication().registerExtension(ConsoleFolding.EP_NAME, object : ConsoleFolding() {
      override fun shouldFoldLine(project: Project, line: String): Boolean {
        return line.contains("FOO")
      }

      override fun getPlaceholderText(project: Project, lines: MutableList<String?>): String {
        return "folded"
      }
    }, console)

    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    assertOneElement(consoleEditor.foldingModel.allFoldRegions)

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    val region = assertOneElement(consoleEditor.foldingModel.allFoldRegions)
    assertEquals("folded", region.placeholderText)
    assertEquals(0, consoleEditor.document.getLineNumber(region.startOffset))
    assertEquals(2, consoleEditor.document.getLineNumber(region.endOffset))

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    assertSize(2, consoleEditor.foldingModel.allFoldRegions)
  }

  fun testSubsequentNonAttachedFoldsAreCombined() {
    ApplicationManager.getApplication().registerExtension(ConsoleFolding.EP_NAME, object : ConsoleFolding() {
      override fun shouldFoldLine(project: Project, line: String): Boolean {
        return line.contains("FOO")
      }

      override fun getPlaceholderText(project: Project, lines: MutableList<String?>): String {
        return "folded"
      }

      override fun shouldBeAttachedToThePreviousLine(): Boolean {
        return false
      }
    }, console)

    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    assertOneElement(consoleEditor.foldingModel.allFoldRegions)

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    val region = assertOneElement(consoleEditor.foldingModel.allFoldRegions)
    assertEquals("folded", region.placeholderText)
    assertEquals(1, consoleEditor.document.getLineNumber(region.startOffset))
    assertEquals(2, consoleEditor.document.getLineNumber(region.endOffset))

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    assertSize(2, consoleEditor.foldingModel.allFoldRegions)
  }

  fun testSubsequentExpandedFoldsAreCombined() {
    ApplicationManager.getApplication().registerExtension(ConsoleFolding.EP_NAME, object : ConsoleFolding() {
      override fun shouldFoldLine(project: Project, line: String): Boolean {
        return line.contains("FOO")
      }

      override fun getPlaceholderText(project: Project, lines: MutableList<String?>): String {
        return "folded"
      }
    }, console)

    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    assertOneElement(consoleEditor.foldingModel.allFoldRegions)

    consoleEditor.foldingModel.runBatchFoldingOperation(Runnable {
      val firstRegion = assertOneElement(consoleEditor.foldingModel.allFoldRegions)
      firstRegion.setExpanded(true)
    })

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    val region = assertOneElement(consoleEditor.foldingModel.allFoldRegions)
    assertEquals("folded", region.placeholderText)
    assertEquals(0, consoleEditor.document.getLineNumber(region.startOffset))
    assertEquals(2, consoleEditor.document.getLineNumber(region.endOffset))

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    val regions = consoleEditor.foldingModel.allFoldRegions
    assertSize(2, regions)
    assertTrue(regions[0].isExpanded())
    assertFalse(regions[1].isExpanded())
  }

  fun testClearPrintConsoleSizeConsistency() {
    withCycleConsoleNoFolding(1000) { consoleView: ConsoleViewImpl ->
      val text = "long text"
      consoleView.print(text, ConsoleViewContentType.SYSTEM_OUTPUT)
      consoleView.waitAllRequests()
      //editor contains `text`
      assertEquals(text.length, consoleView.editor!!.document.textLength)
      consoleView.clear()
      consoleView.print(text, ConsoleViewContentType.SYSTEM_OUTPUT)
      //assert console's editor text which is about to be cleared is not added
      assertEquals(text.length, consoleView.contentSize)
    }
  }

  fun testSubsequentExpandedNonAttachedFoldsAreCombined() {
    ApplicationManager.getApplication().registerExtension(ConsoleFolding.EP_NAME, object : ConsoleFolding() {
      override fun shouldFoldLine(project: Project, line: String): Boolean {
        return line.contains("FOO")
      }

      override fun getPlaceholderText(project: Project, lines: MutableList<String?>): String {
        return "folded"
      }

      override fun shouldBeAttachedToThePreviousLine(): Boolean {
        return false
      }
    }, console)

    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    assertOneElement(consoleEditor.foldingModel.allFoldRegions)

    consoleEditor.foldingModel.runBatchFoldingOperation(Runnable {
      val firstRegion = assertOneElement(consoleEditor.foldingModel.allFoldRegions)
      firstRegion.setExpanded(true)
    })

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    val region = assertOneElement(consoleEditor.foldingModel.allFoldRegions)
    assertEquals("folded", region.placeholderText)
    assertEquals(1, consoleEditor.document.getLineNumber(region.startOffset))
    assertEquals(2, consoleEditor.document.getLineNumber(region.endOffset))

    console.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT)
    console.flushDeferredText()

    val regions = consoleEditor.foldingModel.allFoldRegions
    assertSize(2, regions)
    assertTrue(regions[0].isExpanded())
    assertFalse(regions[1].isExpanded())
  }

  private val allRangeHighlighters: MutableList<RangeHighlighter>
    get() {
      val model = DocumentMarkupModel.forDocument(consoleEditor.document, project, true)
      val highlighters = model.allHighlighters
      Arrays.sort(highlighters, Comparator.comparingInt { obj: RangeMarker -> obj.startOffset }.thenComparingInt { obj: RangeMarker -> obj.endOffset })
      return mutableListOf(*highlighters)
    }

  private fun assertMarkersEqual(actual: MutableList<out RangeHighlighter>, vararg expected: ExpectedHighlighter) {
    assertEquals(expected.size, actual.size)
    for (i in expected.indices) {
      assertMarkerEquals(expected[i], actual[i])
    }
  }

  private fun assertMarkerEquals(expected: ExpectedHighlighter, actual: RangeHighlighter) {
    assertEquals(expected.myStartOffset, actual.startOffset)
    assertEquals(expected.myEndOffset, actual.endOffset)
    assertEquals(expected.myContentType.attributes, actual.getTextAttributes(null))
    assertEquals(expected.myContentType.attributesKey, actual.textAttributesKey)
  }

  private class ExpectedHighlighter(
    val myStartOffset: Int,
    val myEndOffset: Int,
    val myContentType: ConsoleViewContentType
  )

  fun testTypingMustLeadToMergedUserInputTokensAtTheDocumentEnd() {
    console.type(consoleEditor, "/")
    console.flushDeferredText()
    assertEquals("/", console.text)
    assertMarkersEqual(allRangeHighlighters,
                       ExpectedHighlighter(0, 1, ConsoleViewContentType.USER_INPUT)
    )

    console.type(consoleEditor, "/")
    console.flushDeferredText()
    assertEquals("//", console.text)
    assertMarkersEqual(allRangeHighlighters,
                       ExpectedHighlighter(0, 2, ConsoleViewContentType.USER_INPUT)
    )
  }

  fun testEnterDuringTypingMustSeparateUserInputTokens() {
    console.type(consoleEditor, "2")
    console.flushDeferredText()
    assertEquals("2", console.text)
    assertMarkersEqual(allRangeHighlighters,
                       ExpectedHighlighter(0, 1, ConsoleViewContentType.USER_INPUT)
    )
    console.type(consoleEditor, "\n")
    console.flushDeferredText()
    console.type(consoleEditor, "3")
    console.flushDeferredText()
    console.type(consoleEditor, "\n")
    console.flushDeferredText()
    assertEquals("2\n3\n", console.text)
    assertMarkersEqual(allRangeHighlighters,
                       ExpectedHighlighter(0, 1, ConsoleViewContentType.USER_INPUT),
                       ExpectedHighlighter(1, 2, ConsoleViewContentType.USER_INPUT),
                       ExpectedHighlighter(2, 3, ConsoleViewContentType.USER_INPUT),
                       ExpectedHighlighter(3, 4, ConsoleViewContentType.USER_INPUT)
    )
  }

  companion object {
    @JvmStatic
    fun createConsole(usePredefinedMessageFilter: Boolean, project: Project): ConsoleViewImpl {
      val console = ConsoleViewImpl(project,
                                    GlobalSearchScope.allScope(project),
                                    false,
                                    usePredefinedMessageFilter)
      console.component // initConsoleEditor()
      val processHandler: ProcessHandler = NopProcessHandler()
      processHandler.startNotify()
      console.attachToProcess(processHandler)
      return console
    }

    private fun typeIn(editor: Editor, c: Char) {
      EditorActionManager.getInstance()
      val action = TypedAction.getInstance()
      val dataContext = (editor as EditorEx).dataContext

      action.actionPerformed(editor, c, dataContext)
    }
  }
}
