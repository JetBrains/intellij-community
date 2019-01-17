// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ConsoleFolding;
import com.intellij.execution.filters.ConsoleInputFilterProvider;
import com.intellij.execution.filters.InputFilter;
import com.intellij.execution.process.AnsiEscapeDecoderTest;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

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
      Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
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
    EditorActionManager actionManager = EditorActionManager.getInstance();
    DataContext dataContext = DataManager.getInstance().getDataContext(console.getComponent());
    TypedAction action = actionManager.getTypedAction();
    action.actionPerformed(console.getEditor(), 'h', dataContext);
    assertEquals(1, console.getContentSize());
  }

  public void testTypingAfterMultipleCR() {
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    final TestDataProvider dataContext = new TestDataProvider(getProject());

    final ConsoleViewImpl console = myConsole;
    final Editor editor = console.getEditor();
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

  @NotNull
  static ConsoleViewImpl createConsole() {
    return createConsole(false);
  }

  @NotNull
  private static ConsoleViewImpl createConsole(boolean usePredefinedMessageFilter) {
    Project project = getProject();
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
      PlatformTestUtil.startPerformanceTest("console print", 11_000, () -> {
        console.clear();
        for (int i=0; i<10_000_000; i++) {
          console.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }
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

  private static void withCycleConsoleNoFolding(int capacityKB, Consumer<? super ConsoleViewImpl> runnable) {
    ExtensionPoint<ConsoleFolding> point = Extensions.getRootArea().getExtensionPoint(ConsoleFolding.EP_NAME);
    ConsoleFolding[] extensions = point.getExtensions();
    for (ConsoleFolding extension : extensions) {
      point.unregisterExtension(extension);
    }

    UISettings uiSettings = UISettings.getInstance();
    boolean oldUse = uiSettings.getOverrideConsoleCycleBufferSize();
    int oldSize = uiSettings.getConsoleCycleBufferSizeKb();

    uiSettings.setOverrideConsoleCycleBufferSize(true);
    uiSettings.setConsoleCycleBufferSizeKb(capacityKB);
    // create new to reflect changed buffer size
    ConsoleViewImpl console = createConsole(true);
    try {
      runnable.consume(console);
    }
    finally {
      Disposer.dispose(console);
      uiSettings.setOverrideConsoleCycleBufferSize(oldUse);
      uiSettings.setConsoleCycleBufferSizeKb(oldSize);


      for (ConsoleFolding extension : extensions) {
        point.registerExtension(extension);
      }
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
    TypedAction action = EditorActionManager.getInstance().getTypedAction();
    DataContext dataContext = ((EditorEx)editor).getDataContext();

    action.actionPerformed(editor, c, dataContext);
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

  private static void backspace(ConsoleViewImpl consoleView) {
    Editor editor = consoleView.getEditor();
    Set<Shortcut> backShortcuts = new THashSet<>(Arrays.asList(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE).getShortcutSet().getShortcuts()));
    List<AnAction> actions = ActionUtil.getActions(consoleView.getEditor().getContentComponent());
    AnAction handler = ContainerUtil.find(actions,
      a -> new THashSet<>(Arrays.asList(a.getShortcutSet().getShortcuts())).equals(backShortcuts));
    CommandProcessor.getInstance().executeCommand(getProject(),
                                                  () -> EditorTestUtil.executeAction(editor, true, handler),
                                                  "", null, editor.getDocument());
  }

  public void testCRPrintCR() throws Exception {
    for (int i=0;i<25;i++) {
      myConsole.print("\r"+i, ConsoleViewContentType.NORMAL_OUTPUT);
      Thread.sleep(100);
    }
    myConsole.flushDeferredText();
    myConsole.waitAllRequests();
    assertEquals("24", myConsole.getText());
  }

  public void testInputFilter() {
    Disposer.dispose(myConsole); // have to re-init extensions
    List<Pair<String, ConsoleViewContentType>> registered = new ArrayList<>();
    ConsoleInputFilterProvider crazyProvider = project -> new InputFilter[]{
      (text, contentType) -> {
        registered.add(Pair.create(text, contentType));
        return Collections.singletonList(Pair.create("+!" + text + "-!", contentType));
      }
    };
    PlatformTestUtil.registerExtension(ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS, crazyProvider, getTestRootDisposable());
    myConsole = createConsole();
    StringBuilder expectedText = new StringBuilder();
    List<Pair<String, ConsoleViewContentType>> expectedRegisteredTokens = new ArrayList<>();
    for (int i=0;i<25;i++) {
      String chunk = i + "";
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

  private void assertPrintedText(@NotNull String[] textToPrint, @NotNull String expectedText) {
    myConsole.clear();
    myConsole.waitAllRequests();
    Assert.assertEquals("", myConsole.getText());
    for (String text : textToPrint) {
      myConsole.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
    }
    myConsole.flushDeferredText();
    Assert.assertEquals(expectedText, myConsole.getText());

    myConsole.clear();
    myConsole.waitAllRequests();
    Assert.assertEquals("", myConsole.getText());
    for (String text : textToPrint) {
      myConsole.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
      myConsole.flushDeferredText();
    }
    Assert.assertEquals(expectedText, myConsole.getText());
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
        Assert.assertEquals((long) printCount * nCopies * "\na\nc".length(), myConsole.getContentSize());
      }
    }).assertTiming();
  }

  public void testBackspaceChangesHighlightingRanges1() {
    myConsole.print("Starting\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("Hello", ConsoleViewContentType.ERROR_OUTPUT);
    myConsole.print("\b\b\b\bDone", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.flushDeferredText();
    Assert.assertEquals("Starting\nHDone", myConsole.getText());

    List<RangeHighlighter> actualHighlighters = getAllRangeHighlighters();
    assertMarkersEqual(ContainerUtil.newArrayList(
      new ExpectedHighlighter(0, 9, ConsoleViewContentType.NORMAL_OUTPUT),
      new ExpectedHighlighter(9, 10, ConsoleViewContentType.ERROR_OUTPUT),
      new ExpectedHighlighter(10, 14, ConsoleViewContentType.SYSTEM_OUTPUT)
    ), actualHighlighters);
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

    Assert.assertEquals("Ready\nSteady\nGototokenX\n_Done", myConsole.getText());
    List<RangeHighlighter> actualHighlighters = getAllRangeHighlighters();
    assertMarkersEqual(ContainerUtil.newArrayList(
      new ExpectedHighlighter(0, 13, ConsoleViewContentType.NORMAL_OUTPUT),  // Ready\nSteady\n
      new ExpectedHighlighter(13, 15, ConsoleViewContentType.ERROR_OUTPUT),  // Go
      new ExpectedHighlighter(15, 17, ConsoleViewContentType.SYSTEM_OUTPUT), // to
      new ExpectedHighlighter(17, 24, ConsoleViewContentType.NORMAL_OUTPUT), // tokenX\n
      new ExpectedHighlighter(24, 29, ConsoleViewContentType.SYSTEM_OUTPUT)  // Done
    ), actualHighlighters);
  }

  public void testBackspaceChangesHighlightingRanges3() {
    myConsole.print("Test1\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsole.print("Test2", ConsoleViewContentType.SYSTEM_OUTPUT);
    myConsole.print("\b\b\b\b\b\b", ConsoleViewContentType.ERROR_OUTPUT);
    myConsole.flushDeferredText();

    Assert.assertEquals("Test1\n", myConsole.getText());
    List<RangeHighlighter> actualHighlighters = getAllRangeHighlighters();
    assertMarkersEqual(ContainerUtil.newArrayList(
      new ExpectedHighlighter(0, 6, ConsoleViewContentType.NORMAL_OUTPUT)  // Test1\n
    ), actualHighlighters);
  }

  @NotNull
  private List<RangeHighlighter> getAllRangeHighlighters() {
    MarkupModel model = DocumentMarkupModel.forDocument(myConsole.getEditor().getDocument(), getProject(), true);
    RangeHighlighter[] highlighters = model.getAllHighlighters();
    Arrays.sort(highlighters, (r1, r2) -> {
      int startOffsetDiff = r1.getStartOffset() - r2.getStartOffset();
      if (startOffsetDiff != 0) return startOffsetDiff;
      return r1.getEndOffset() - r2.getEndOffset();
    });
    return Arrays.asList(highlighters);
  }

  private static void assertMarkersEqual(@NotNull List<ExpectedHighlighter> expected, @NotNull List<RangeHighlighter> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      assertMarkerEquals(expected.get(i), actual.get(i));
    }
  }

  private static void assertMarkerEquals(@NotNull ExpectedHighlighter expected, @NotNull RangeHighlighter actual) {
    assertEquals(expected.myStartOffset, actual.getStartOffset());
    assertEquals(expected.myEndOffset, actual.getEndOffset());
    assertEquals(expected.myContentType.getAttributes(), actual.getTextAttributes());
  }

  private static class ExpectedHighlighter {
    private final int myStartOffset;
    private final int myEndOffset;
    private final ConsoleViewContentType myContentType;

    private ExpectedHighlighter(int startOffset, int endOffset, @NotNull ConsoleViewContentType contentType) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myContentType = contentType;
    }
  }
}
