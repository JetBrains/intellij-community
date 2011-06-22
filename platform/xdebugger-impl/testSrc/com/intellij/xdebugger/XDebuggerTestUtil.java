/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.xdebugger;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.ui.DebuggerIcons;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class XDebuggerTestUtil {
  private static final int TIMEOUT = 25000;

  private XDebuggerTestUtil() {
  }

  public static <B extends XBreakpoint<?>> void assertBreakpointValidity(Project project,
                                                                         VirtualFile file,
                                                                         int line,
                                                                         boolean validity,
                                                                         String errorMessage,
                                                                         Class<? extends XBreakpointType<B, ?>> breakpointType) {
    XLineBreakpointType type = (XLineBreakpointType)XDebuggerUtil.getInstance().findBreakpointType(breakpointType);
    XBreakpointManager manager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointImpl breakpoint = (XLineBreakpointImpl)manager.findBreakpointAtLine(type, file, line);
    Assert.assertNotNull(breakpoint);
    Assert.assertEquals(validity ? DebuggerIcons.VERIFIED_BREAKPOINT_ICON : DebuggerIcons.INVALID_BREAKPOINT_ICON, breakpoint.getIcon());
    Assert.assertEquals(errorMessage, breakpoint.getErrorMessage());
  }

  public static void toggleBreakpoint(Project project, VirtualFile file, int line) {
    XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line);
  }

  public static void assertPosition(XSourcePosition pos, VirtualFile file, int line) {
    Assert.assertNotNull(pos);
    Assert.assertEquals(file, pos.getFile());
    if (line != -1) Assert.assertEquals(line, pos.getLine());
  }

  public static void assertCurrentPosition(XDebugSession session, VirtualFile file, int line) {
    assertPosition(session.getCurrentPosition(), file, line);
  }

  public static XExecutionStack getActiveThread(@NotNull XDebugSession session) {
    return session.getSuspendContext().getActiveExecutionStack();
  }

  public static List<XStackFrame> collectStacks(@NotNull XDebugSession session) throws InterruptedException {
    return collectStacks(null, session);
  }

  public static List<XStackFrame> collectStacks(@Nullable XExecutionStack thread, @NotNull XDebugSession session) throws InterruptedException {
    return collectStacks(thread == null ? getActiveThread(session) : thread);
  }

  public static List<XStackFrame> collectStacks(@NotNull XExecutionStack thread) throws InterruptedException {
    XTestStackFrameContainer container = new XTestStackFrameContainer();
    thread.computeStackFrames(0, container);
    return container.waitFor(TIMEOUT * 2).first;
  }

  public static Pair<XValue, String> evaluate(XDebugSession session, String expression) throws InterruptedException {
    XDebuggerEvaluator evaluator = session.getCurrentStackFrame().getEvaluator();
    XTestEvaluationCallback callback = new XTestEvaluationCallback();
    evaluator.evaluate(expression, callback, session.getCurrentPosition());
    return callback.waitFor(TIMEOUT);
  }

  public static void waitForSwing() throws InterruptedException, InvocationTargetException {
    final com.intellij.util.concurrency.Semaphore s = new com.intellij.util.concurrency.Semaphore();
    s.down();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        s.up();
      }
    });
    s.waitForUnsafe();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
      }
    });
  }

  @NotNull
  public static XValue findVar(Collection<XValue> vars, String name) {
    for (XValue each : vars) {
      if (each instanceof XNamedValue) {
        if (((XNamedValue)each).getName().equals(name)) return each;
      }
    }
    throw new AssertionError("var '" + name + "' not found");
  }

  public static XTestValueNode computePresentation(@NotNull XValue value) throws InterruptedException {
    XTestValueNode node = new XTestValueNode();
    if (value instanceof XNamedValue) {
      node.myName = ((XNamedValue)value).getName();
    }
    value.computePresentation(node, XValuePlace.TREE);
    Assert.assertTrue(node.waitFor(TIMEOUT));
    return node;
  }

  public static void assertVariable(XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) throws InterruptedException {
    XTestValueNode node = computePresentation(var);

    Assert.assertEquals(name, node.myName);
    if (type != null) Assert.assertEquals(type, node.myType);
    if (value != null) Assert.assertEquals(value, node.myValue);
    if (hasChildren != null) Assert.assertEquals((boolean)hasChildren, node.myHasChildren);
  }

  public static void assertVariableValue(XValue var, @Nullable String name, @Nullable String value) throws InterruptedException {
    assertVariable(var, name, null, value, null);
  }

  public static void assertVariableValue(Collection<XValue> vars, @Nullable String name, @Nullable String value) throws InterruptedException {
    assertVariableValue(findVar(vars, name), name, value);
  }

  public static void assertVariableValueMatches(Collection<XValue> vars, @Nullable String name, String valuePattern) throws InterruptedException {
    assertVariableValueMatches(findVar(vars, name), name, valuePattern);
  }

  public static void assertVariableValueMatches(XValue var, String name, String valuePattern) throws InterruptedException {
    XTestValueNode node = computePresentation(var);
    Assert.assertEquals(name, node.myName);
    Assert.assertTrue(node.myValue, node.myValue.matches(valuePattern));
  }

  public static void assertVariables(List<XValue> vars, String... names) throws InterruptedException {
    List<String> expectedNames = new ArrayList<String>(Arrays.asList(names));

    List<String> actualNames = new ArrayList<String>();
    for (XValue each : vars) {
      actualNames.add(computePresentation(each).myName);
    }

    Collections.sort(actualNames);
    Collections.sort(expectedNames);
    UsefulTestCase.assertOrderedEquals(actualNames, expectedNames);
  }

  public static void assertSourcePosition(final XValue value, VirtualFile file, int offset) {
    final XTestNavigatable n = new XTestNavigatable();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        value.computeSourcePosition(n);
      }
    });
    Assert.assertNotNull(n.myPosition);
    Assert.assertEquals(file, n.myPosition.getFile());
    Assert.assertEquals(offset, n.myPosition.getOffset());
  }

  public static boolean waitFor(Semaphore semaphore, long timeoutInMillis) throws InterruptedException {
    return semaphore.tryAcquire(timeoutInMillis, TimeUnit.MILLISECONDS);
  }

  public static void assertVariable(Collection<XValue> vars, String name, String type, String value, Boolean hasChildren)
    throws InterruptedException {
    assertVariable(findVar(vars, name), name, type, value, hasChildren);
  }

  @NotNull
  public static String getConsoleText(final @NotNull ConsoleViewImpl consoleView) {
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        consoleView.flushDeferredText();
      }
    }.execute();

    return consoleView.getEditor().getDocument().getText();
  }

  public static <T extends XBreakpointType> XBreakpoint addBreakpoint(@NotNull final Project project,
                                                               @NotNull final Class<T> exceptionType,
                                                               @NotNull final XBreakpointProperties properties) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XBreakpointType[] types = XBreakpointUtil.getBreakpointTypes();
    final Ref<XBreakpoint> breakpoint = Ref.create(null);
    for (XBreakpointType type : types) {
      if (exceptionType.isInstance(type)) {
        final T breakpointType = exceptionType.cast(type);
        new WriteAction() {
          @Override
          protected void run(Result result) throws Throwable {
            breakpoint.set(breakpointManager.addBreakpoint(breakpointType, properties));
          }
        }.execute();
        break;
      }
    }
    return breakpoint.get();
  }

  public static void setBreakpointCondition(Project project, int line, final String condition) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : breakpointManager.getAllBreakpoints()) {
      if (breakpoint instanceof XLineBreakpoint) {
        final XLineBreakpoint lineBreakpoint = (XLineBreakpoint)breakpoint;

        if (lineBreakpoint.getLine() == line) {
          new WriteAction() {
            @Override
            protected void run(Result result) throws Throwable {
              lineBreakpoint.setCondition(condition);
            }
          }.execute();
        }
      }
    }
  }

  public static class XTestStackFrameContainer extends XTestContainer<XStackFrame> implements XExecutionStack.XStackFrameContainer {
    public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
      addChildren(stackFrames, last);
    }

    public void errorOccured(String errorMessage) {
      setErrorMessage(errorMessage);
    }
  }

  public static class XTestNavigatable implements XNavigatable {
    private XSourcePosition myPosition;

    @Override
    public void setSourcePosition(@Nullable XSourcePosition sourcePosition) {
      myPosition = sourcePosition;
    }

    public XSourcePosition getPosition() {
      return myPosition;
    }
  }
}
