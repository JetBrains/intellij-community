/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.util.Alarm;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

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

  public void testTypeText() throws Exception {
    ConsoleViewImpl console = myConsole;
    console.print("Initial", ConsoleViewContentType.NORMAL_OUTPUT);
    console.flushDeferredText();
    console.clear();
    console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT);
    assertEquals(2, console.getContentSize());
  }

  public void testConsolePrintsSomethingAfterDoubleClear() throws Exception {
    ConsoleViewImpl console = myConsole;
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
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

  public void testConsolePrintsSomethingAfterClearPrintScroll() throws Exception {
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
    ConsoleViewImpl console = myConsole;
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    for (int i = 0; i < 100; i++) {
      // To speed up test execution, set -Dconsole.flush.delay.ms=5 to reduce ConsoleViewImpl.DEFAULT_FLUSH_DELAY
      //System.out.println("Attempt #" + i);
      console.clear(); // 1-st clear
      CountDownLatch latch = new CountDownLatch(1);
      alarm.addRequest(() -> {
        console.clear(); // 2-nd clear
        console.print("Test", ConsoleViewContentType.NORMAL_OUTPUT);
        latch.countDown();
      }, 0);
      UIUtil.dispatchAllInvocationEvents(); // flush 1-st clear request
      latch.await();
      UIUtil.dispatchAllInvocationEvents(); // flush 2-nd clear request
      while (console.hasDeferredOutput()) {
        UIUtil.dispatchAllInvocationEvents();
        TimeoutUtil.sleep(5);
      }
      assertEquals("Test", console.getText());
    }
  }

  public void testTypeInEmptyConsole() throws Exception {
    ConsoleViewImpl console = myConsole;
    console.clear();
    EditorActionManager actionManager = EditorActionManager.getInstance();
    DataContext dataContext = DataManager.getInstance().getDataContext(console.getComponent());
    TypedAction action = actionManager.getTypedAction();
    action.actionPerformed(console.getEditor(), 'h', dataContext);
    assertEquals(1, console.getContentSize());
  }

  public void testTypingAfterMultipleCR() throws Exception {
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

  @NotNull
  private static ConsoleViewImpl createConsole() {
    Project project = getProject();
    ConsoleViewImpl console = new ConsoleViewImpl(project,
                                                  GlobalSearchScope.allScope(project),
                                                  false,
                                                  false);
    console.getComponent();
    ProcessHandler processHandler = new MyProcessHandler();
    processHandler.startNotify();
    console.attachToProcess(processHandler);
    return console;
  }

  private static class MyProcessHandler extends ProcessHandler {
    @Override
    protected void destroyProcessImpl() {
      notifyProcessTerminated(0);
    }

    @Override
    protected void detachProcessImpl() {
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return null;
    }
  }
}
