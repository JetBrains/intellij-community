/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

public class ConsoleViewImplTest extends LightPlatformTestCase {

  public void testTypeText() throws Exception {
    final ConsoleViewImpl console = createConsole();
    console.print("Initial", ConsoleViewContentType.NORMAL_OUTPUT);
    console.flushDeferredText();
    try {
      console.clear();
      console.print("Hi", ConsoleViewContentType.NORMAL_OUTPUT);
      assertEquals(2, console.getContentSize());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      Disposer.dispose(console);
    }
  }

  public void testTypingAfterMultipleCR() throws Exception {
    final EditorActionManager actionManager = EditorActionManager.getInstance();
    final TypedAction typedAction = actionManager.getTypedAction();
    final TestDataProvider dataContext = new TestDataProvider(getProject());

    final ConsoleViewImpl console = createConsole();
    final Editor editor = console.getEditor();
    try {
      console.print("System output\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      console.print("\r\r\r\r\r\r\r", ConsoleViewContentType.NORMAL_OUTPUT);
      console.flushDeferredText();

      typedAction.actionPerformed(editor, '1', dataContext);
      typedAction.actionPerformed(editor, '2', dataContext);

      assertEquals("System output\n12", editor.getDocument().getText());
    }
    finally {
      Disposer.dispose(console);
    }
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
