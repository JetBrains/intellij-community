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
package com.intellij.xdebugger;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

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
    assertNotNull(breakpoint);
    assertEquals(validity ? AllIcons.Debugger.Db_verified_breakpoint : AllIcons.Debugger.Db_invalid_breakpoint, breakpoint.getIcon());
    assertEquals(errorMessage, breakpoint.getErrorMessage());
  }

  public static void toggleBreakpoint(Project project, VirtualFile file, int line) {
    XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line);
  }

  public static <P extends XBreakpointProperties> XBreakpoint<P> insertBreakpoint(final Project project,
                                                                                  final P properties,
                                                                                  final Class<? extends XBreakpointType<XBreakpoint<P>, P>> typeClass) {
    return new WriteAction<XBreakpoint<P>>() {
      protected void run(@NotNull final Result<XBreakpoint<P>> result) {
        result.setResult(XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(
          XBreakpointType.EXTENSION_POINT_NAME.findExtension(typeClass), properties));
      }
    }.execute().getResultObject();
  }

  public static void removeBreakpoint(final Project project, final XBreakpoint<?> breakpoint) {
    new WriteAction() {
      protected void run(@NotNull final Result result) {
        XDebuggerManager.getInstance(project).getBreakpointManager().removeBreakpoint(breakpoint);
      }
    }.execute();
  }

  public static void assertPosition(XSourcePosition pos, VirtualFile file, int line) throws IOException {
    assertNotNull("No current position", pos);
    assertEquals(new File(file.getPath()).getCanonicalPath(), new File(pos.getFile().getPath()).getCanonicalPath());
    if (line != -1) assertEquals(line, pos.getLine());
  }

  public static void assertCurrentPosition(XDebugSession session, VirtualFile file, int line) throws IOException {
    assertPosition(session.getCurrentPosition(), file, line);
  }

  public static XExecutionStack getActiveThread(@NotNull XDebugSession session) {
    return session.getSuspendContext().getActiveExecutionStack();
  }

  public static List<XStackFrame> collectFrames(@NotNull XDebugSession session) throws InterruptedException {
    return collectFrames(null, session);
  }

  public static List<XStackFrame> collectFrames(@Nullable XExecutionStack thread, @NotNull XDebugSession session)
    throws InterruptedException {
    return collectStacks(thread == null ? getActiveThread(session) : thread);
  }
  
  public static String getFramePresentation(XStackFrame frame) {
    TextTransferable.ColoredStringBuilder builder = new TextTransferable.ColoredStringBuilder();
    frame.customizePresentation(builder);
    return builder.getBuilder().toString();
  }

  public static List<XStackFrame> collectStacks(@NotNull XExecutionStack thread) throws InterruptedException {
    return collectStacks(thread, TIMEOUT * 2);
  }

  public static List<XStackFrame> collectStacks(XExecutionStack thread, long timeout) throws InterruptedException {
    XTestStackFrameContainer container = new XTestStackFrameContainer();
    thread.computeStackFrames(0, container);
    return container.waitFor(timeout).first;
  }

  public static List<XValue> collectVariables(XStackFrame frame) throws InterruptedException {
    XTestCompositeNode container = new XTestCompositeNode();
    frame.computeChildren(container);
    return container.waitFor(TIMEOUT).first;
  }

  public static Pair<XValue, String> evaluate(XDebugSession session, XExpression expression) {
    return evaluate(session, expression, TIMEOUT);
  }

  public static Pair<XValue, String> evaluate(XDebugSession session, String expression) {
    return evaluate(session, XExpressionImpl.fromText(expression), TIMEOUT);
  }

  public static Pair<XValue, String> evaluate(XDebugSession session, String expression, long timeout) {
    return evaluate(session, XExpressionImpl.fromText(expression), timeout);
  }

  private static Pair<XValue, String> evaluate(XDebugSession session, XExpression expression, long timeout) {
    XStackFrame frame = session.getCurrentStackFrame();
    assertNotNull(frame);
    XDebuggerEvaluator evaluator = frame.getEvaluator();
    assertNotNull(evaluator);
    XTestEvaluationCallback callback = new XTestEvaluationCallback();
    evaluator.evaluate(expression, callback, session.getCurrentPosition());
    return callback.waitFor(timeout);
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
    StringBuilder names = new StringBuilder();
    for (XValue each : vars) {
      if (each instanceof XNamedValue) {
        String eachName = ((XNamedValue)each).getName();
        if (eachName.equals(name)) return each;

        if (names.length() > 0) names.append(", ");
        names.append(eachName);
      }
    }
    throw new AssertionError("var '" + name + "' not found among " + names);
  }

  public static XTestValueNode computePresentation(@NotNull XValue value) throws InterruptedException {
    return computePresentation(value, TIMEOUT);
  }

  public static XTestValueNode computePresentation(XValue value, long timeout) throws InterruptedException {
    XTestValueNode node = new XTestValueNode();
    if (value instanceof XNamedValue) {
      node.myName = ((XNamedValue)value).getName();
    }
    value.computePresentation(node, XValuePlace.TREE);
    node.waitFor(timeout);
    return node;
  }

  public static void assertVariable(XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) throws InterruptedException {
    XTestValueNode node = computePresentation(var);

    if (name != null) assertEquals(name, node.myName);
    if (type != null) assertEquals(type, node.myType);
    if (value != null) assertEquals(value, node.myValue);
    if (hasChildren != null) assertEquals(hasChildren, node.myHasChildren);
  }

  public static void assertVariableValue(XValue var, @Nullable String name, @Nullable String value) throws InterruptedException {
    assertVariable(var, name, null, value, null);
  }

  public static void assertVariableValue(Collection<XValue> vars, @Nullable String name, @Nullable String value)
    throws InterruptedException {
    assertVariableValue(findVar(vars, name), name, value);
  }

  public static void assertVariableValueMatches(@NotNull Collection<XValue> vars,
                                                @Nullable String name,
                                                @Nullable @Language("RegExp") String valuePattern) throws InterruptedException {
    assertVariableValueMatches(findVar(vars, name), name, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable @Language("RegExp") String valuePattern) throws InterruptedException {
    assertVariableValueMatches(var, name, null, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern) throws InterruptedException {
    XTestValueNode node = computePresentation(var);
    if (name != null) assertEquals(name, node.myName);
    if (type != null) assertEquals(type, node.myType);
    if (valuePattern != null) {
      assertTrue("Expected value" + valuePattern + " Actual value: " + node.myValue, node.myValue.matches(valuePattern));
    }
  }

  public static void assertVariableTypeMatches(@NotNull Collection<XValue> vars,
                                               @Nullable String name,
                                               @Nullable @Language("RegExp") String typePattern) throws InterruptedException {
    assertVariableTypeMatches(findVar(vars, name), name, typePattern);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @Language("RegExp") String typePattern) throws InterruptedException {
    XTestValueNode node = computePresentation(var);
    if (name != null) {
      assertEquals(name, node.myName);
    }
    if (typePattern != null) {
      assertTrue("Expected type: " + typePattern + " Actual type: " + node.myType, node.myType.matches(typePattern));
    }
  }

  public static void assertVariableFullValue(@NotNull XValue var,
                                             @Nullable String value) throws InterruptedException {
    XTestValueNode node = computePresentation(var);
    final String[] result = new String[1];

    node.myFullValueEvaluator.startEvaluation(new XFullValueEvaluator.XFullValueEvaluationCallback() {
      @Override
      public void evaluated(@NotNull String fullValue) {
        result[0] = fullValue;
      }

      @Override
      public void evaluated(@NotNull String fullValue, @Nullable Font font) {
        result[0] = fullValue;
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        result[0] = errorMessage;
      }

      @Override
      public boolean isObsolete() {
        return false;
      }
    });

    assertEquals(value, result[0]);
  }

  public static void assertVariableFullValue(Collection<XValue> vars, @Nullable String name, @Nullable String value)
    throws InterruptedException {
    assertVariableFullValue(findVar(vars, name), value);
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

  public static void assertVariablesContain(List<XValue> vars, String... names) throws InterruptedException {
    List<String> expectedNames = new ArrayList<String>(Arrays.asList(names));

    List<String> actualNames = new ArrayList<String>();
    for (XValue each : vars) {
      actualNames.add(computePresentation(each).myName);
    }

    expectedNames.removeAll(actualNames);
    assertTrue("Missing variables:" + StringUtil.join(expectedNames, ", ")
                        + "\nAll Variables: " + StringUtil.join(actualNames, ", "),
                        expectedNames.isEmpty()
    );
  }

  public static void assertSourcePosition(final XValue value, VirtualFile file, int offset) {
    final XTestNavigatable n = new XTestNavigatable();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        value.computeSourcePosition(n);
      }
    });
    assertNotNull(n.myPosition);
    assertEquals(file, n.myPosition.getFile());
    assertEquals(offset, n.myPosition.getOffset());
  }

  public static boolean waitFor(Semaphore semaphore, long timeoutInMillis) {
    long end = System.currentTimeMillis() + timeoutInMillis;
    long remaining = timeoutInMillis;
    do {
      try {
        return semaphore.tryAcquire(remaining, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException ignored) {
        remaining = end - System.currentTimeMillis();
      }
    } while (remaining > 0);
    return false;
  }

  public static void assertVariable(Collection<XValue> vars,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) throws InterruptedException {
    assertVariable(findVar(vars, name), name, type, value, hasChildren);
  }

  @NotNull
  public static String getConsoleText(final @NotNull ConsoleViewImpl consoleView) {
    new WriteAction() {
      protected void run(@NotNull Result result) throws Throwable {
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
          protected void run(@NotNull Result result) throws Throwable {
            breakpoint.set(breakpointManager.addBreakpoint(breakpointType, properties));
          }
        }.execute();
        break;
      }
    }
    return breakpoint.get();
  }

  public static void removeAllBreakpoints(@NotNull final Project project) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XBreakpoint<?>[] breakpoints = getBreakpoints(breakpointManager);
    for (final XBreakpoint b : breakpoints) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          breakpointManager.removeBreakpoint(b);
        }
      }.execute();
    }
  }

  public static XBreakpoint<?>[] getBreakpoints(final XBreakpointManager breakpointManager) {
    return ApplicationManager.getApplication().runReadAction(new Computable<XBreakpoint<?>[]>() {
      public XBreakpoint<?>[] compute() {
        return breakpointManager.getAllBreakpoints();
      }
    });
  }

  public static <B extends XBreakpoint<?>>
  void setDefaultBreakpointEnabled(@NotNull final Project project, Class<? extends XBreakpointType<B, ?>> bpTypeClass, boolean enabled) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XBreakpointType<B, ?> bpType = XDebuggerUtil.getInstance().findBreakpointType(bpTypeClass);
    XBreakpoint<?> bp = breakpointManager.getDefaultBreakpoint(bpType);
    if (bp != null) {
      bp.setEnabled(enabled);
    }
  }

  public static void setBreakpointCondition(Project project, int line, final String condition) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : getBreakpoints(breakpointManager)) {
      if (breakpoint instanceof XLineBreakpoint) {
        final XLineBreakpoint lineBreakpoint = (XLineBreakpoint)breakpoint;

        if (lineBreakpoint.getLine() == line) {
          new WriteAction() {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              lineBreakpoint.setCondition(condition);
            }
          }.execute();
        }
      }
    }
  }

  public static void setBreakpointLogExpression(Project project, int line, final String logExpression) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : getBreakpoints(breakpointManager)) {
      if (breakpoint instanceof XLineBreakpoint) {
        final XLineBreakpoint lineBreakpoint = (XLineBreakpoint)breakpoint;

        if (lineBreakpoint.getLine() == line) {
          new WriteAction() {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              lineBreakpoint.setLogExpression(logExpression);
              lineBreakpoint.setLogMessage(true);
            }
          }.execute();
        }
      }
    }
  }

  public static void disposeDebugSession(final XDebugSession debugSession) {
    new WriteAction() {
      protected void run(@NotNull Result result) throws Throwable {
        XDebugSessionImpl session = (XDebugSessionImpl)debugSession;
        Disposer.dispose(session.getSessionTab());
        Disposer.dispose(session.getConsoleView());
      }
    }.execute();
  }

  public static void assertVariable(Pair<XValue, String> varAndErrorMessage,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) throws InterruptedException {
    assertNull(varAndErrorMessage.second);
    assertVariable(varAndErrorMessage.first, name, type, value, hasChildren);
  }

  public static String assertVariableExpression(XValue desc, String expectedExpression) {
    String expression = desc.getEvaluationExpression();
    assertEquals(expectedExpression, expression);
    return expression;
  }

  public static class XTestStackFrameContainer extends XTestContainer<XStackFrame> implements XExecutionStack.XStackFrameContainer {
    public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
      addChildren(stackFrames, last);
    }

    @Override
    public void errorOccurred(@NotNull String errorMessage) {
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
