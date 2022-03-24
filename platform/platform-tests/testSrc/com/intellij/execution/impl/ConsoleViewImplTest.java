// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.filters.*;
import com.intellij.execution.process.AnsiEscapeDecoderTest;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.LineSeparator;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConsoleViewImplTest extends LightPlatformTestCase {
  private ConsoleViewImpl myConsole;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myConsole = createConsole();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myConsole);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testTypeText() {
    ConsoleViewImpl console = myConsole;
    console.print("Initial", ConsoleViewContentType.NORMAL_OUTPUT);
    console.flushDeferredText();
    console.clear();
    console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT);
    console.waitAllRequests();
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(2, console.getContentSize());
  }

  public void testTypeBeforeSelectionMustNotLeadToInvalidOffset() {
    ConsoleViewImpl console = myConsole;
    console.print("Initial", ConsoleViewContentType.USER_INPUT);
    console.flushDeferredText();
    console.clear();
    console.print("Hi", ConsoleViewContentType.USER_INPUT);
    console.waitAllRequests();
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(2, console.getContentSize());
    assertEquals(2, console.getEditor().getCaretModel().getOffset());
    console.getEditor().getCaretModel().setCaretsAndSelections(Collections.singletonList(new CaretState(new LogicalPosition(0,0),
                                                                                                        new LogicalPosition(0,0),
                                                                                                        new LogicalPosition(0,2))));
    assertEquals(0, console.getEditor().getCaretModel().getOffset());
    typeIn(console.getEditor(), 'x');
    console.waitAllRequests();
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(1, console.getContentSize());
  }

  public void testConsolePrintsSomethingAfterDoubleClear() throws Exception {
    ConsoleViewImpl console = myConsole;
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD,getTestRootDisposable());
    CountDownLatch latch = new CountDownLatch(1);
    alarm.addRequest(() -> {
      console.clear();
      console.clear();
      console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT);
      latch.countDown();
    }, 0);
    latch.await();
    while (console.hasDeferredOutput()) {
      UIUtil.dispatchAllInvocationEvents();
      TimeoutUtil.sleep(5);
    }
    assertEquals("Test", console.getText());
  }

  public void testConsolePrintsSomethingAfterClearPrintScroll() {
    ConsoleViewImpl console = myConsole;
    Alarm alarm = new Alarm(getTestRootDisposable());
    for (int i=0; i<1000/*000*/; i++) {
      CountDownLatch latch = new CountDownLatch(1);
      alarm.addRequest(() -> {
        console.clear();
        console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT);
        console.scrollTo(0);
        latch.countDown();
      }, 0);
      while (latch.getCount() != 0) {
        UIUtil.dispatchAllInvocationEvents();
      }
      while (console.hasDeferredOutput()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      assertEquals("Test", console.getText());
    }
  }

  public void testClearAndPrintWhileAnotherClearExecution() throws Exception {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ConsoleViewImpl console = myConsole;
    for (int i = 0; i < 100; i++) {
      // To speed up test execution, set -Dconsole.flush.delay.ms=5 to reduce ConsoleViewImpl.DEFAULT_FLUSH_DELAY
      //System.out.println("Attempt #" + i);
      console.clear(); // 1-st clear
      CountDownLatch latch = new CountDownLatch(1);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        console.clear(); // 2-nd clear
        console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT);
        latch.countDown();
      });
      UIUtil.dispatchAllInvocationEvents(); // flush 1-st clear request
      assertTrue(latch.await(30, TimeUnit.SECONDS));
      UIUtil.dispatchAllInvocationEvents(); // flush 2-nd clear request
      while (console.hasDeferredOutput()) {
        UIUtil.dispatchAllInvocationEvents();
        TimeoutUtil.sleep(1);
      }
      assertEquals("iteration " + i, "Test", console.getText());
      future.get();
    }
  }

  public void testTypeInEmptyConsole() {
    ConsoleViewImpl console = myConsole;
    console.clear();
    EditorActionManager.getInstance();
    DataContext dataContext = DataManager.getInstance().getDataContext(console.getComponent());
    TypedAction action = TypedAction.getInstance();
    action.actionPerformed(console.getEditor(), 'h', dataContext);
    assertEquals(1, console.getContentSize());
  }

  public void testTypingAfterMultipleCR() {
    final TypedAction typedAction = TypedAction.getInstance();

    final ConsoleViewImpl console = myConsole;
    final Editor editor = console.getEditor();
    final DataContext dataContext = ((EditorEx)editor).getDataContext();
    console.print("System output\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    console.print("\r\r\r\r\r\r\r", ConsoleViewContentType.NORMAL_OUTPUT);
    console.flushDeferredText();

    typedAction.actionPerformed(editor, '1', dataContext);
    typedAction.actionPerformed(editor, '2', dataContext);

    assertEquals("System output\n12", editor.getDocument().getText());
  }

  public void testCRLF() {
    ConsoleViewImpl console = myConsole;
    console.clear();
    console.print("Hello\r", ConsoleViewContentType.NORMAL_OUTPUT);
    console.print("\nWorld", ConsoleViewContentType.NORMAL_OUTPUT);
    console.flushDeferredText();
    assertEquals("Hello\nWorld", console.getText());
  }

  public void testCRTypeCR() {
    myConsole.print("\rHi\rMr\rSmith", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    assertEquals("Smith", myConsole.getText());
  }

  public void testCRTypeTearCR() {
    myConsole.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT);

    myConsole.print("\rMr\rSmith", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    assertEquals("Smith", myConsole.getText());
  }
  public void testCRTypeFlushCR() {
    myConsole.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    assertEquals("Hi", myConsole.getText());
    myConsole.print("\rMr\rSmith", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    assertEquals("Smith", myConsole.getText());
  }

  public void testCaretAfterMultilineOutput() {
    assertCaretAt(0, 0);
    myConsole.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    assertCaretAt(0, 2);
    myConsole.print("\nprompt:", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    assertCaretAt(1, 7);
  }

  private void assertCaretAt(int line, int column) {
    LogicalPosition position = myConsole.getEditor().getCaretModel().getLogicalPosition();
    assertEquals(line, position.line);
    assertEquals(column, position.column);
  }

  @NotNull
  private ConsoleViewImpl createConsole() {
    return createConsole(false, getProject());
  }

  @NotNull
  static ConsoleViewImpl createConsole(boolean usePredefinedMessageFilter, Project project) {
    ConsoleViewImpl console = new ConsoleViewImpl(project,
                                                  GlobalSearchScope.allScope(project),
                                                  false,
                                                  usePredefinedMessageFilter);
    console.getComponent(); // initConsoleEditor()
    ProcessHandler processHandler = new NopProcessHandler();
    processHandler.startNotify();
    console.attachToProcess(processHandler);
    return console;
  }

  public void testDoNotRemoveEverythingWhenOneCharIsPrintedAfterLargeText() {
    withCycleConsoleNoFolding(1, console -> {
      console.print(StringUtil.repeat("a", 5000), ConsoleViewContentType.NORMAL_OUTPUT);
      console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      console.waitAllRequests();
      assertEquals(StringUtil.repeat("a", 1023) + "\n", console.getText());
    });
  }

  public void testPerformance() {
    withCycleConsoleNoFolding(100, console ->
      PlatformTestUtil.startPerformanceTest("console print", 15000, () -> {
        console.clear();
        for (int i=0; i<10_000_000; i++) {
          console.print("xxx\n", ConsoleViewContentType.NORMAL_OUTPUT);
          console.print("yyy\n", ConsoleViewContentType.SYSTEM_OUTPUT);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }
        LightPlatformCodeInsightTestCase.type('\n', console.getEditor(), getProject());
        console.waitAllRequests();
      }).assertTiming());
  }

  public void testLargeConsolePerformance() {
    withCycleConsoleNoFolding(UISettings.getInstance().getConsoleCycleBufferSizeKb(), console ->
      PlatformTestUtil.startPerformanceTest("console print", 15_000, () -> {
        console.clear();
        for (int i=0; i<20_000_000; i++) {
          console.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT);
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        console.waitAllRequests();
      }).assertTiming());
  }

  public void testPerformanceOfMergeableTokens() {
    withCycleConsoleNoFolding(1000, console ->
      PlatformTestUtil.startPerformanceTest("console print with mergeable tokens", 3500, () -> {
        console.clear();
        for (int i=0; i<10_000_000; i++) {
          console.print("xxx\n", ConsoleViewContentType.NORMAL_OUTPUT);
        }
        UIUtil.dispatchAllInvocationEvents();
        console.waitAllRequests();
        MarkupModel model = DocumentMarkupModel.forDocument(console.getEditor().getDocument(), getProject(), true);
        RangeHighlighter highlighter = assertOneElement(model.getAllHighlighters());
        assertEquals(new TextRange(0, console.getEditor().getDocument().getTextLength()), TextRange.create(highlighter));
      }).assertTiming());
  }

  private void withCycleConsoleNoFolding(int capacityKB, Consumer<? super ConsoleViewImpl> runnable) {
    UISettings uiSettings = UISettings.getInstance();
    boolean oldUse = uiSettings.getOverrideConsoleCycleBufferSize();
    int oldSize = uiSettings.getConsoleCycleBufferSizeKb();

    uiSettings.setOverrideConsoleCycleBufferSize(true);
    uiSettings.setConsoleCycleBufferSizeKb(capacityKB);
    // create new to reflect changed buffer size
    ConsoleViewImpl console = createConsole(true, getProject());

    ExtensionPoint<ConsoleFolding> point = ConsoleFolding.EP_NAME.getPoint();
    ((ExtensionPointImpl<ConsoleFolding>)point).maskAll(Collections.emptyList(), console, false);
    assertEmpty(point.getExtensions());

    try {
      runnable.consume(console);
    }
    finally {
      Disposer.dispose(console);
      uiSettings.setOverrideConsoleCycleBufferSize(oldUse);
      uiSettings.setConsoleCycleBufferSizeKb(oldSize);
    }
  }

  public void testBigOutputDoesntMemoryOverflow() {
    withCycleConsoleNoFolding(100, console -> {
      for (int i=0;i<10_000_000; i++) {
        console.print("---- "+i+"----", ConsoleViewContentType.NORMAL_OUTPUT);
      }
    });
  }

  public void testCanPrintUserInputFromBackground() throws ExecutionException, InterruptedException {
    Future<?> future = JobScheduler.getScheduler().submit(() -> myConsole.print("input", ConsoleViewContentType.USER_INPUT));

    while (!future.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    future.get();
  }

  public void testUserInputIsSentToProcessAfterNewLinePressed() {
    Process testProcess = AnsiEscapeDecoderTest.createTestProcess();
    ByteArrayOutputStream outputStream = (ByteArrayOutputStream)testProcess.getOutputStream();

    AnsiEscapeDecoderTest.withProcessHandlerFrom(testProcess, handler ->
      withCycleConsoleNoFolding(100, console -> {
        console.attachToProcess(handler);
        outputStream.reset();
        console.print("I", ConsoleViewContentType.USER_INPUT);
        console.waitAllRequests();
        assertEquals(0, outputStream.size());
        console.print("K", ConsoleViewContentType.USER_INPUT);
        console.waitAllRequests();
        assertEquals(0, outputStream.size());
        console.print("\n", ConsoleViewContentType.USER_INPUT);
        console.waitAllRequests();
        assertEquals("IK\n", outputStream.toString());
    }));
  }

  public void testUserTypingIsSentToProcessAfterNewLinePressed() {
    Process testProcess = AnsiEscapeDecoderTest.createTestProcess();
    ByteArrayOutputStream outputStream = (ByteArrayOutputStream)testProcess.getOutputStream();

    AnsiEscapeDecoderTest.withProcessHandlerFrom(testProcess, handler ->
      withCycleConsoleNoFolding(100, console -> {
        console.attachToProcess(handler);
        outputStream.reset();
        Editor editor = console.getEditor();
        typeIn(editor, 'X');
        console.waitAllRequests();
        assertEquals(0, outputStream.size());

        typeIn(editor, 'Y');
        console.waitAllRequests();
        assertEquals(0, outputStream.size());

        typeIn(editor, '\n');
        console.waitAllRequests();
        assertEquals(3, outputStream.size());
        assertEquals("XY\n", outputStream.toString());
    }));
  }

  private static void typeIn(Editor editor, char c) {
    EditorActionManager.getInstance();
    TypedAction action = TypedAction.getInstance();
    DataContext dataContext = ((EditorEx)editor).getDataContext();

    action.actionPerformed(editor, c, dataContext);
  }

  public void testCompleteLinesWhenMessagesArePrintedConcurrently() throws ExecutionException, InterruptedException {
    assertCompleteLines("stdout ", 20000, 2, "stderr ", 20000, 3, 10);
    assertCompleteLines("stdout ", 20000, 5, "stderr ", 20000, 7, 10);
    assertCompleteLines("info: ", 20000, 11, "error: ", 20000, 13, 10);
    assertCompleteLines("Hello", 40000, 199, "Bye", 40000, 101, 5);
  }

  private void assertCompleteLines(@NotNull String stdoutLinePrefix, int stdoutLines, int stdoutBufferSize,
                                   @NotNull String stderrLinePrefix, int stderrLines, int stderrBufferSize,
                                   int rerunCount) throws ExecutionException, InterruptedException {
    ProcessHandler processHandler = new NopProcessHandler();
    myConsole.attachToProcess(processHandler);
    for (int i = 0; i < rerunCount; i++) {
      myConsole.clear();
      myConsole.waitAllRequests();
      int estimatedPrintedChars = stdoutLines * (stdoutLinePrefix.length() + Integer.toString(stdoutLines).length() + 1) +
                                  stderrLines * (stderrLinePrefix.length() + Integer.toString(stderrLines).length() + 1);
      assertTrue(ConsoleBuffer.getCycleBufferSize() > estimatedPrintedChars);
      Future<?> stdout = sendMessagesInBackground(processHandler, stdoutLinePrefix, stdoutLines, ProcessOutputType.STDOUT, stdoutBufferSize);
      Future<?> stderr = sendMessagesInBackground(processHandler, stderrLinePrefix, stderrLines, ProcessOutputType.STDERR, stderrBufferSize);
      stdout.get();
      stderr.get();
      ((ConsoleViewRunningState)myConsole.getState()).getStreamsSynchronizer().waitForAllFlushed();
      myConsole.flushDeferredText();
      String text = myConsole.getEditor().getDocument().getText();
      String[] lines = StringUtil.splitByLinesKeepSeparators(text);
      int readStdoutLines = 0;
      int readStderrLines = 0;
      for (String line : lines) {
        if (line.startsWith(stdoutLinePrefix)) {
          assertEquals(stdoutLinePrefix + (readStdoutLines + 1) + LineSeparator.LF.getSeparatorString(), line);
          readStdoutLines++;
        }
        else {
          assertEquals(stderrLinePrefix + (readStderrLines + 1) + LineSeparator.LF.getSeparatorString(), line);
          readStderrLines++;
        }
      }
      assertEquals(stdoutLines, readStdoutLines);
      assertEquals(stderrLines, readStderrLines);
    }
  }

  @NotNull
  private static Future<?> sendMessagesInBackground(@NotNull ProcessHandler processHandler,
                                                    @NotNull String linePrefix,
                                                    int lineCount,
                                                    @NotNull ProcessOutputType outputType,
                                                    int bufferSize) {
    return ApplicationManager.getApplication().executeOnPooledThread(() -> {
      int bufferRestSize = bufferSize;
      for (int i = 1; i <= lineCount; i++) {
        String text = linePrefix + i + LineSeparator.LF.getSeparatorString();
        int printedTextSize = 0;
        while (printedTextSize < text.length()) {
          if (bufferRestSize == 0) {
            bufferRestSize = bufferSize;
          }
          int endInd = Math.min(printedTextSize + bufferRestSize, text.length());
          String textToPrint = text.substring(printedTextSize, endInd);
          processHandler.notifyTextAvailable(textToPrint, outputType);
          bufferRestSize -= textToPrint.length();
          printedTextSize += textToPrint.length();
        }
      }
    });
  }

  public void testBackspaceDoesDeleteTheLastTypedChar() {
    final ConsoleViewImpl console = myConsole;
    final Editor editor = console.getEditor();
    console.print("xxxx", ConsoleViewContentType.NORMAL_OUTPUT);
    console.print("a", ConsoleViewContentType.USER_INPUT);
    console.print("b", ConsoleViewContentType.USER_INPUT);
    console.print("c", ConsoleViewContentType.USER_INPUT);
    console.print("d", ConsoleViewContentType.USER_INPUT);
    console.flushDeferredText();
    assertEquals("xxxxabcd", editor.getDocument().getText());

    backspace(console);
    assertEquals("xxxxabc", editor.getDocument().getText());
    backspace(console);
    assertEquals("xxxxab", editor.getDocument().getText());
    backspace(console);
    assertEquals("xxxxa", editor.getDocument().getText());
    backspace(console);
    assertEquals("xxxx", editor.getDocument().getText());
  }

  private void backspace(ConsoleViewImpl consoleView) {
    Editor editor = consoleView.getEditor();
    Set<Shortcut> backShortcuts = ContainerUtil.set(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE).getShortcutSet().getShortcuts());
    List<AnAction> actions = ActionUtil.getActions(consoleView.getEditor().getContentComponent());
    AnAction handler = ContainerUtil.find(actions,
      a -> ContainerUtil.set(a.getShortcutSet().getShortcuts()).equals(backShortcuts));
    CommandProcessor.getInstance().executeCommand(getProject(),
                                                  () -> EditorTestUtil.executeAction(editor, true, handler),
                                                  "", null, editor.getDocument());
  }

  public void testCRPrintCR() throws Exception {
    for (int i=0;i<25;i++) {
      myConsole.print("\r"+i, ConsoleViewContentType.NORMAL_OUTPUT);
      //noinspection BusyWait
      Thread.sleep(100);
    }
    myConsole.flushDeferredText();
    myConsole.waitAllRequests();
    assertEquals("24", myConsole.getText());
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public void testInputFilter() {
    Disposer.dispose(myConsole); // have to re-init extensions
    List<Pair<String, ConsoleViewContentType>> registered = new ArrayList<>();
    ConsoleInputFilterProvider crazyProvider = project -> new InputFilter[]{
      (text, contentType) -> {
        registered.add(Pair.create(text, contentType));
        return Collections.singletonList(Pair.create("+!" + text + "-!", contentType));
      }
    };
    ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS.getPoint().registerExtension(crazyProvider, getTestRootDisposable());
    myConsole = createConsole();
    StringBuilder expectedText = new StringBuilder();
    List<Pair<String, ConsoleViewContentType>> expectedRegisteredTokens = new ArrayList<>();
    for (int i=0;i<25;i++) {
      String chunk = String.valueOf(i);
      myConsole.print(chunk, ConsoleViewContentType.USER_INPUT);
      expectedText.append("+!" + i + "-!");
      expectedRegisteredTokens.add(Pair.create(chunk, ConsoleViewContentType.USER_INPUT));

      for (int j = 0; j < chunk.length(); j++) {
        typeIn(myConsole.getEditor(), chunk.charAt(j));
      }
      chunk.chars().forEach(c->{
        expectedText.append("+!" + (char)c + "-!");
        expectedRegisteredTokens.add(Pair.create(String.valueOf((char)c), ConsoleViewContentType.USER_INPUT));
      });
    }
    myConsole.flushDeferredText();
    myConsole.waitAllRequests();
    assertEquals(expectedText.toString(), myConsole.getText());
    assertEquals(expectedRegisteredTokens, registered);
  }

  public void testConsoleDependentInputFilter() {
    Disposer.dispose(myConsole); // have to re-init extensions
    ConsoleDependentInputFilterProvider filterProvider = new ConsoleDependentInputFilterProvider() {
      @Override
      public @NotNull List<InputFilter> getDefaultFilters(@NotNull ConsoleView consoleView,
                                                          @NotNull Project project,
                                                          @NotNull GlobalSearchScope scope) {
        return List.of((text, contentType) -> Collections.singletonList(Pair.create("!" + text + "!", contentType)));
      }
    };
    ExtensionTestUtil.maskExtensions(
      ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS,
      ContainerUtil.newArrayList(filterProvider),
      getTestRootDisposable());

    myConsole = createConsole(true, getProject());
    myConsole.print("Foo", ConsoleViewContentType.USER_INPUT);
    myConsole.print("Bar", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("Baz", ConsoleViewContentType.ERROR_OUTPUT);
    myConsole.flushDeferredText();
    myConsole.waitAllRequests();
    assertEquals("!Foo!!Bar!!Baz!", myConsole.getText());
  }

  public void testCustomFiltersPrecedence() {
    HyperlinkInfo predefinedHyperlink = project -> {};
    Filter predefinedFilter = (line, entireLength) ->  new Filter.Result(0, 1, predefinedHyperlink);
    HyperlinkInfo customHyperlink = project -> {};
    Filter customFilter = (line, entireLength) ->  new Filter.Result(0, 10, customHyperlink);

    Disposer.dispose(myConsole); // have to re-init extensions

    ConsoleFilterProvider predefinedProvider = project -> new Filter[] { predefinedFilter };
    ExtensionTestUtil.maskExtensions(
      ConsoleFilterProvider.FILTER_PROVIDERS,
      ContainerUtil.newArrayList(predefinedProvider),
      getTestRootDisposable());

    myConsole = createConsole(true, getProject());
    myConsole.addMessageFilter(customFilter);
    myConsole.print("foo bar buz test", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();
    myConsole.waitAllRequests();

    EditorHyperlinkSupport hyperlinks = myConsole.getHyperlinks();
    assertNotNull(hyperlinks.getHyperlinkAt(0));
    assertEquals(customHyperlink, hyperlinks.getHyperlinkAt(0));
    assertNotNull(hyperlinks.getHyperlinkAt(10));
    assertEquals(customHyperlink, hyperlinks.getHyperlinkAt(10));
  }

  public void testBackspaceDeletesPreviousOutput() {
    assertPrintedText(new String[]{"Test", "\b"}, "Tes");
    assertPrintedText(new String[]{"Test", "\b", "\b"}, "Te");
    assertPrintedText(new String[]{"Hello", "\b\b\b\b", "allo"}, "Hallo");
    assertPrintedText(new String[]{"A\b\b\bha\bop", "\bul\bpp", "\b\bsl\be"}, "house");
    assertPrintedText(new String[]{"\b\bTest\b\b\b\b\b", "Done", "\b\b\b"}, "D");
    assertPrintedText(new String[]{"\b\b\b\b\b\b\b"}, "");
    assertPrintedText(new String[]{"The\b\b\b\b", "first lint", "\be\n",
      "\b\b\bsecond lone", "\b\b\bine\n",
      "\bthird\b\b\b\b\b\b\b\bthe third line"}, "first line\nsecond line\nthe third line");
    assertPrintedText(new String[]{"\n\n\b\bStart\nEnq\bd"}, "\n\nStart\nEnd");
    assertPrintedText(new String[]{"\nEnter your pass:", "\rsecreq\bt"}, "\nsecret");
    assertPrintedText(new String[]{"test\b\b\b\b\b\bline1\n\blinee\b2\r\n\blin\b\b\b\bline?", "\b3\n", "Done\n"},
                      "line1\nline2\nline3\nDone\n");
  }

  private void assertPrintedText(String @NotNull [] textToPrint, @NotNull String expectedText) {
    myConsole.clear();
    myConsole.waitAllRequests();
    assertEquals("", myConsole.getText());
    for (String text : textToPrint) {
      myConsole.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
    }
    myConsole.flushDeferredText();
    assertEquals(expectedText, myConsole.getText());

    myConsole.clear();
    myConsole.waitAllRequests();
    assertEquals("", myConsole.getText());
    for (String text : textToPrint) {
      myConsole.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
      myConsole.flushDeferredText();
    }
    assertEquals(expectedText, myConsole.getText());
  }

  public void testBackspacePerformance() {
    int nCopies = 10000;
    String in = StringUtil.repeat("\na\nb\bc", nCopies);
    PlatformTestUtil.startPerformanceTest("print newlines with backspace", 5000, () -> {
      for (int i = 0; i < 2; i++) {
        myConsole.clear();
        int printCount = ConsoleBuffer.getCycleBufferSize() / in.length();
        for (int j = 0; j < printCount; j++) {
          myConsole.print(in, ConsoleViewContentType.NORMAL_OUTPUT);
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        myConsole.waitAllRequests();
        assertEquals((long) printCount * nCopies * "\na\nc".length(), myConsole.getContentSize());
      }
    }).assertTiming();
  }

  public void testBackspaceChangesHighlightingRanges1() {
    myConsole.print("Starting\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("Hello", ConsoleViewContentType.ERROR_OUTPUT);
    myConsole.print("\b\b\b\bDone", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.flushDeferredText();
    assertEquals("Starting\nHDone", myConsole.getText());

    List<RangeHighlighter> actualHighlighters = getAllRangeHighlighters();
    assertMarkersEqual(actualHighlighters,
      new ExpectedHighlighter(0, 9, ConsoleViewContentType.NORMAL_OUTPUT),
      new ExpectedHighlighter(9, 10, ConsoleViewContentType.ERROR_OUTPUT),
      new ExpectedHighlighter(10, 14, ConsoleViewContentType.SYSTEM_OUTPUT)
    );
  }

  public void testBackspaceChangesHighlightingRanges2() {
    myConsole.print("Ready\n\bSet\b\b\b\b\bSteady\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("\b\b\bGo", ConsoleViewContentType.ERROR_OUTPUT);
    myConsole.print("token1", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.print("\b\b\b\btoken2\bX\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("temp", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("\b\b\b\b\b\b\b\b\b", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("_", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.print("Done", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.flushDeferredText();

    assertEquals("Ready\nSteady\nGototokenX\n_Done", myConsole.getText());
    assertMarkersEqual(getAllRangeHighlighters(),
      new ExpectedHighlighter(0, 13, ConsoleViewContentType.NORMAL_OUTPUT),  // Ready\nSteady\n
      new ExpectedHighlighter(13, 15, ConsoleViewContentType.ERROR_OUTPUT),  // Go
      new ExpectedHighlighter(15, 17, ConsoleViewContentType.SYSTEM_OUTPUT), // to
      new ExpectedHighlighter(17, 24, ConsoleViewContentType.NORMAL_OUTPUT), // tokenX\n
      new ExpectedHighlighter(24, 29, ConsoleViewContentType.SYSTEM_OUTPUT)  // Done
    );
  }

  public void testBackspaceChangesHighlightingRanges3() {
    myConsole.print("Test1\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("Test2", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.print("\b\b\b\b\b\b", ConsoleViewContentType.ERROR_OUTPUT);
    myConsole.flushDeferredText();

    assertEquals("Test1\n", myConsole.getText());
    assertMarkersEqual(getAllRangeHighlighters(),
      new ExpectedHighlighter(0, 6, ConsoleViewContentType.NORMAL_OUTPUT)  // Test1\n
    );
  }

  public void testSubsequentFoldsAreCombined() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ConsoleFolding.EP_NAME, new ConsoleFolding() {
      @Override
      public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
        return line.contains("FOO");
      }

      @NotNull
      @Override
      public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
        return "folded";
      }
    }, myConsole);

    Editor editor = myConsole.getEditor();

    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    assertOneElement(editor.getFoldingModel().getAllFoldRegions());

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    FoldRegion region = assertOneElement(editor.getFoldingModel().getAllFoldRegions());
    assertEquals("folded", region.getPlaceholderText());
    assertEquals(0, editor.getDocument().getLineNumber(region.getStartOffset()));
    assertEquals(2, editor.getDocument().getLineNumber(region.getEndOffset()));

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    assertSize(2, editor.getFoldingModel().getAllFoldRegions());
  }

  public void testSubsequentNonAttachedFoldsAreCombined() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ConsoleFolding.EP_NAME, new ConsoleFolding() {
      @Override
      public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
        return line.contains("FOO");
      }

      @NotNull
      @Override
      public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
        return "folded";
      }

      @Override
      public boolean shouldBeAttachedToThePreviousLine() {
        return false;
      }
    }, myConsole);

    Editor editor = myConsole.getEditor();

    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    assertOneElement(editor.getFoldingModel().getAllFoldRegions());

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    FoldRegion region = assertOneElement(editor.getFoldingModel().getAllFoldRegions());
    assertEquals("folded", region.getPlaceholderText());
    assertEquals(1, editor.getDocument().getLineNumber(region.getStartOffset()));
    assertEquals(2, editor.getDocument().getLineNumber(region.getEndOffset()));

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    assertSize(2, editor.getFoldingModel().getAllFoldRegions());
  }

  public void testSubsequentExpandedFoldsAreCombined() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ConsoleFolding.EP_NAME, new ConsoleFolding() {
      @Override
      public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
        return line.contains("FOO");
      }

      @NotNull
      @Override
      public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
        return "folded";
      }
    }, myConsole);

    Editor editor = myConsole.getEditor();

    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    assertOneElement(editor.getFoldingModel().getAllFoldRegions());

    myConsole.getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
      FoldRegion firstRegion = assertOneElement(editor.getFoldingModel().getAllFoldRegions());
      firstRegion.setExpanded(true);
    });

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    FoldRegion region = assertOneElement(editor.getFoldingModel().getAllFoldRegions());
    assertEquals("folded", region.getPlaceholderText());
    assertEquals(0, editor.getDocument().getLineNumber(region.getStartOffset()));
    assertEquals(2, editor.getDocument().getLineNumber(region.getEndOffset()));

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    assertSize(2, regions);
    assertTrue(regions[0].isExpanded());
    assertFalse(regions[1].isExpanded());
  }

  public void testClearPrintConsoleSizeConsistency() {
    withCycleConsoleNoFolding(1000, consoleView -> {
      String text = "long text";
      consoleView.print(text, ConsoleViewContentType.SYSTEM_OUTPUT);
      consoleView.waitAllRequests();
      //editor contains `text`
      assertEquals(text.length(), consoleView.getEditor().getDocument().getTextLength());
      consoleView.clear();
      consoleView.print(text, ConsoleViewContentType.SYSTEM_OUTPUT);
      //assert console's editor text which is about to be cleared is not added
      assertEquals(text.length(), consoleView.getContentSize());
    });
  }

  public void testSubsequentExpandedNonAttachedFoldsAreCombined() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ConsoleFolding.EP_NAME, new ConsoleFolding() {
      @Override
      public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
        return line.contains("FOO");
      }

      @NotNull
      @Override
      public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
        return "folded";
      }

      @Override
      public boolean shouldBeAttachedToThePreviousLine() {
        return false;
      }
    }, myConsole);

    Editor editor = myConsole.getEditor();

    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    assertOneElement(editor.getFoldingModel().getAllFoldRegions());

    myConsole.getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
      FoldRegion firstRegion = assertOneElement(editor.getFoldingModel().getAllFoldRegions());
      firstRegion.setExpanded(true);
    });

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    FoldRegion region = assertOneElement(editor.getFoldingModel().getAllFoldRegions());
    assertEquals("folded", region.getPlaceholderText());
    assertEquals(1, editor.getDocument().getLineNumber(region.getStartOffset()));
    assertEquals(2, editor.getDocument().getLineNumber(region.getEndOffset()));

    myConsole.print("a FOO a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("a BAR a\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.flushDeferredText();

    FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    assertSize(2, regions);
    assertTrue(regions[0].isExpanded());
    assertFalse(regions[1].isExpanded());
  }

  @NotNull
  private List<RangeHighlighter> getAllRangeHighlighters() {
    MarkupModel model = DocumentMarkupModel.forDocument(myConsole.getEditor().getDocument(), getProject(), true);
    RangeHighlighter[] highlighters = model.getAllHighlighters();
    Arrays.sort(highlighters, Comparator.comparingInt(RangeMarker::getStartOffset).thenComparingInt(RangeMarker::getEndOffset));
    return Arrays.asList(highlighters);
  }

  private static void assertMarkersEqual(@NotNull List<? extends RangeHighlighter> actual, @NotNull ExpectedHighlighter @NotNull ... expected) {
    assertEquals(expected.length, actual.size());
    for (int i = 0; i < expected.length; i++) {
      assertMarkerEquals(expected[i], actual.get(i));
    }
  }

  private static void assertMarkerEquals(@NotNull ExpectedHighlighter expected, @NotNull RangeHighlighter actual) {
    assertEquals(expected.myStartOffset, actual.getStartOffset());
    assertEquals(expected.myEndOffset, actual.getEndOffset());
    assertEquals(expected.myContentType.getAttributes(), actual.getTextAttributes(null));
    assertEquals(expected.myContentType.getAttributesKey(), actual.getTextAttributesKey());
  }

  private static final class ExpectedHighlighter {
    private final int myStartOffset;
    private final int myEndOffset;
    private final ConsoleViewContentType myContentType;

    private ExpectedHighlighter(int startOffset, int endOffset, @NotNull ConsoleViewContentType contentType) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myContentType = contentType;
    }
  }

  public void testTypingMustLeadToMergedUserInputTokensAtTheDocumentEnd() {
    myConsole.type(myConsole.getEditor(), "/");
    myConsole.flushDeferredText();
    assertEquals("/", myConsole.getText());
    assertMarkersEqual(getAllRangeHighlighters(),
      new ExpectedHighlighter(0, 1, ConsoleViewContentType.USER_INPUT)
    );

    myConsole.type(myConsole.getEditor(), "/");
    myConsole.flushDeferredText();
    assertEquals("//", myConsole.getText());
    assertMarkersEqual(getAllRangeHighlighters(),
      new ExpectedHighlighter(0, 2, ConsoleViewContentType.USER_INPUT)
    );
  }
}
