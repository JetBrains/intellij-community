/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl;

import com.intellij.concurrency.JobScheduler;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.*;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
      JobScheduler.getScheduler().execute(() -> {
        console.clear(); // 2-nd clear
        console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT);
        latch.countDown();
      });
      UIUtil.dispatchAllInvocationEvents(); // flush 1-st clear request
      assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
      UIUtil.dispatchAllInvocationEvents(); // flush 2-nd clear request
      while (console.hasDeferredOutput()) {
        UIUtil.dispatchAllInvocationEvents();
        TimeoutUtil.sleep(1);
      }
      assertEquals("iteration " + i, "Test", console.getText());
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
    withCycleConsole(100, console ->
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
    withCycleConsole(UISettings.getInstance().getConsoleCycleBufferSizeKb(), console ->
      PlatformTestUtil.startPerformanceTest("console print", 9000, () -> {
        console.clear();
        for (int i=0; i<10_000_000; i++) {
          console.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }
        console.waitAllRequests();
      }).assertTiming());
  }

  public void testPerformanceOfMergeableTokens() {
    withCycleConsole(1000, console ->
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

  private static void withCycleConsole(int capacityKB, Consumer<ConsoleViewImpl> runnable) {
    boolean oldUse = UISettings.getInstance().getOverrideConsoleCycleBufferSize();
    int oldSize = UISettings.getInstance().getConsoleCycleBufferSizeKb();

    UISettings.getInstance().setOverrideConsoleCycleBufferSize(true);
    UISettings.getInstance().setConsoleCycleBufferSizeKb(capacityKB);
    // create new to reflect changed buffer size
    ConsoleViewImpl console = createConsole(true);
    try {
      runnable.consume(console);
    }
    finally {
      Disposer.dispose(console);
      UISettings.getInstance().setOverrideConsoleCycleBufferSize(oldUse);
      UISettings.getInstance().setConsoleCycleBufferSizeKb(oldSize);
    }

  }

  public void testBigOutputDoesntMemoryOverflow() {
    withCycleConsole(100, console -> {
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
      withCycleConsole(100, console -> {
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
      withCycleConsole(100, console -> {
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
    AnAction handler = actions.stream()
      .filter(a -> new THashSet<>(Arrays.asList(a.getShortcutSet().getShortcuts())).equals(backShortcuts))
      .findFirst()
      .get();
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
}
