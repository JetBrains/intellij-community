/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class DuplexConsoleActionsTest extends LightPlatformTestCase {
  private Disposable myDisposable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myDisposable);
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testMergeSameConsoles() {
    final ConsoleViewImpl console1 = ConsoleViewImplTest.createConsole();
    final ConsoleViewImpl console2 = ConsoleViewImplTest.createConsole();
    final DuplexConsoleView<ConsoleViewImpl, ConsoleViewImpl> duplexConsoleView = new DuplexConsoleView<>(console1, console2);
    Disposer.register(myDisposable, duplexConsoleView);

    final AnAction[] actions1 = console1.createConsoleActions();
    final AnAction[] actions2 = console2.createConsoleActions();
    final AnAction[] mergedActions = duplexConsoleView.createConsoleActions();
    assertEquals(actions1.length, actions2.length);
    assertEquals(actions1.length, mergedActions.length - 1);
    assertHasActions(mergedActions, "Up", "Down", "Soft Wraps", "Scroll", "Clear", "Print");
  }

  public void testMergeReversedConsoles() {
    final ConsoleViewImpl console1 = ConsoleViewImplTest.createConsole();
    final ConsoleViewImpl console2 = createConsoleWithReversedActions();
    final DuplexConsoleView<ConsoleViewImpl, ConsoleViewImpl> duplexConsoleView = new DuplexConsoleView<>(console1, console2);
    Disposer.register(myDisposable, duplexConsoleView);

    final AnAction[] actions1 = console1.createConsoleActions();
    final AnAction[] actions2 = console2.createConsoleActions();
    final AnAction[] mergedActions = duplexConsoleView.createConsoleActions();
    assertEquals(actions1.length, actions2.length);
    assertEquals(actions1.length, mergedActions.length - 1);
    assertHasActions(mergedActions, "Up", "Down", "Soft Wraps", "Scroll", "Clear", "Print");
  }
  
  public void testMergedClear() {
    final ConsoleViewImpl console1 = ConsoleViewImplTest.createConsole();
    final ConsoleViewImpl console2 = ConsoleViewImplTest.createConsole();
    final DuplexConsoleView<ConsoleViewImpl, ConsoleViewImpl> duplexConsoleView = new DuplexConsoleView<>(console1, console2);
    Disposer.register(myDisposable, duplexConsoleView);
    final AnAction clearAction = findAction(duplexConsoleView.createConsoleActions(), "Clear");
    assertNotNull(clearAction);
    
    console1.print("FooBar", ConsoleViewContentType.NORMAL_OUTPUT);
    console2.print("BazFoo", ConsoleViewContentType.NORMAL_OUTPUT);
    console1.flushDeferredText();
    console2.flushDeferredText();
    
    clearAction.actionPerformed(AnActionEvent.createFromAnAction(clearAction, null, ActionPlaces.EDITOR_TOOLBAR, DataContext.EMPTY_CONTEXT));
    
    assertEquals(0, console1.getContentSize());
    assertEquals(0, console2.getContentSize());
  }

  private static void assertHasActions(AnAction[] mergedActions, String... actionNames) {
    for (String name : actionNames) {
      assertNotNull(findAction(mergedActions, name));
    }
  }
  
  @Nullable
  private static AnAction findAction(@NotNull AnAction[] actions, @NotNull String name) {
    return ContainerUtil.find(actions, action -> action.getTemplatePresentation().toString().contains(name));
  }

  @NotNull
  private static ConsoleViewImpl createConsoleWithReversedActions() {
    Project project = getProject();
    ConsoleViewImpl console = new ConsoleViewImpl(project,
                                                  GlobalSearchScope.allScope(project),
                                                  false,
                                                  false) {
      @NotNull
      @Override
      public AnAction[] createConsoleActions() {
        return ContainerUtil.reverse(Arrays.asList(super.createConsoleActions())).toArray(new AnAction[0]);
      }
    };
    console.getComponent();
    ProcessHandler processHandler = new ConsoleViewImplTest.MyProcessHandler();
    processHandler.startNotify();
    console.attachToProcess(processHandler);
    return console;
  }
}
