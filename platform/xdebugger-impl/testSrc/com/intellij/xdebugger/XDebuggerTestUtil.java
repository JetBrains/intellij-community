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
package com.intellij.xdebugger;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
  private static final int TIMEOUT_MS = 25_000;

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

  @Nullable
  public static XLineBreakpoint toggleBreakpoint(Project project, VirtualFile file, int line) {
    return new WriteAction<XLineBreakpoint>() {
      @Override
      protected void run(@NotNull Result<XLineBreakpoint> result) throws Throwable {
        Promise<XLineBreakpoint> promise =
          ((XDebuggerUtilImpl)XDebuggerUtil.getInstance()).toggleAndReturnLineBreakpoint(project, file, line, false);

        promise.done(result::setResult);
      }
    }.execute().getResultObject();
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

  public static List<XExecutionStack> collectThreads(@NotNull XDebugSession session) throws InterruptedException {
    return collectThreadsWithErrors(session).first;
  }

  public static Pair<List<XExecutionStack>, String> collectThreadsWithErrors(@NotNull XDebugSession session) throws InterruptedException {
    XTestExecutionStackContainer container = new XTestExecutionStackContainer();
    session.getSuspendContext().computeExecutionStacks(container);
    return container.waitFor(TIMEOUT_MS);
  }

  public static List<XStackFrame> collectFrames(@NotNull XDebugSession session) throws InterruptedException {
    return collectFrames(null, session);
  }

  public static List<XStackFrame> collectFrames(@Nullable XExecutionStack thread, @NotNull XDebugSession session)
    throws InterruptedException {
    return collectFrames(thread == null ? getActiveThread(session) : thread);
  }
  
  public static String getFramePresentation(XStackFrame frame) {
    TextTransferable.ColoredStringBuilder builder = new TextTransferable.ColoredStringBuilder();
    frame.customizePresentation(builder);
    return builder.getBuilder().toString();
  }

  public static List<XStackFrame> collectFrames(@NotNull XExecutionStack thread) throws InterruptedException {
    return collectFrames(thread, TIMEOUT_MS * 2);
  }

  public static List<XStackFrame> collectFrames(XExecutionStack thread, long timeout) throws InterruptedException {
    return collectFramesWithError(thread, timeout).first;
  }

  public static Pair<List<XStackFrame>, String> collectFramesWithError(XExecutionStack thread, long timeout) throws InterruptedException {
    XTestStackFrameContainer container = new XTestStackFrameContainer();
    thread.computeStackFrames(0, container);
    return container.waitFor(timeout);
  }

  public static Pair<List<XStackFrame>, XStackFrame> collectFramesWithSelected(@NotNull XDebugSession session, long timeout) throws InterruptedException {
    return collectFramesWithSelected(getActiveThread(session), timeout);
  }

  public static Pair<List<XStackFrame>, XStackFrame> collectFramesWithSelected(XExecutionStack thread, long timeout) throws InterruptedException {
    XTestStackFrameContainer container = new XTestStackFrameContainer();
    thread.computeStackFrames(0, container);
    List<XStackFrame> all = container.waitFor(timeout).first;
    return Pair.create(all, container.frameToSelect);
  }

  /**
   * @deprecated use {@link XDebuggerTestUtil#collectChildren(XValueContainer)}
   */
  @Deprecated
  public static List<XValue> collectVariables(XStackFrame frame) throws InterruptedException {
    return collectChildren(frame);
  }

  public static List<XValue> collectChildren(XValueContainer value) throws InterruptedException {
    XTestCompositeNode container = new XTestCompositeNode();
    value.computeChildren(container);
    return container.waitFor(TIMEOUT_MS).first;
  }

  public static Pair<XValue, String> evaluate(XDebugSession session, XExpression expression) {
    return evaluate(session, expression, TIMEOUT_MS);
  }

  public static Pair<XValue, String> evaluate(XDebugSession session, String expression) {
    return evaluate(session, XExpressionImpl.fromText(expression), TIMEOUT_MS);
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
    ApplicationManager.getApplication().invokeLater(() -> s.up());
    s.waitForUnsafe();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {});
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
    return computePresentation(value, TIMEOUT_MS);
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

  public static void assertVariableValueMatches(@NotNull Collection<XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern) throws InterruptedException {
    assertVariableValueMatches(findVar(vars, name), name, type, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull Collection<XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern,
                                                @Nullable Boolean hasChildren) throws InterruptedException {
    assertVariableValueMatches(findVar(vars, name), name, type, valuePattern, hasChildren);
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
    assertVariableValueMatches(var, name, type, valuePattern, null);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern,
                                                @Nullable Boolean hasChildren) throws InterruptedException {
    XTestValueNode node = computePresentation(var);
    if (name != null) assertEquals(name, node.myName);
    if (type != null) assertEquals(type, node.myType);
    if (valuePattern != null) {
      assertTrue("Expected value: " + valuePattern + " Actual value: " + node.myValue, node.myValue.matches(valuePattern));
    }
    if (hasChildren != null) assertEquals(hasChildren, node.myHasChildren);
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
                                             @Nullable String value) throws Exception {
    XTestValueNode node = computePresentation(var);

    if (value == null) {
      assertNull("full value evaluator should be null", node.myFullValueEvaluator);
    }
    else {
      final FutureResult<String> result = new FutureResult<>();
      node.myFullValueEvaluator.startEvaluation(new XFullValueEvaluator.XFullValueEvaluationCallback() {
        @Override
        public void evaluated(@NotNull String fullValue) {
          result.set(fullValue);
        }

        @Override
        public void evaluated(@NotNull String fullValue, @Nullable Font font) {
          result.set(fullValue);
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          result.set(errorMessage);
        }

        @Override
        public boolean isObsolete() {
          return false;
        }
      });

      assertEquals(value, result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }
  }

  public static void assertVariableFullValue(Collection<XValue> vars, @Nullable String name, @Nullable String value)
    throws Exception {
    assertVariableFullValue(findVar(vars, name), value);
  }

  public static void assertVariables(List<XValue> vars, String... names) throws InterruptedException {
    List<String> expectedNames = new ArrayList<>(Arrays.asList(names));

    List<String> actualNames = new ArrayList<>();
    for (XValue each : vars) {
      actualNames.add(computePresentation(each).myName);
    }

    Collections.sort(actualNames);
    Collections.sort(expectedNames);
    UsefulTestCase.assertOrderedEquals(actualNames, expectedNames);
  }

  public static void assertVariablesContain(List<XValue> vars, String... names) throws InterruptedException {
    List<String> expectedNames = new ArrayList<>(Arrays.asList(names));

    List<String> actualNames = new ArrayList<>();
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
    ApplicationManager.getApplication().runReadAction(() -> value.computeSourcePosition(n));
    assertNotNull(n.myPosition);
    assertEquals(file, n.myPosition.getFile());
    assertEquals(offset, n.myPosition.getOffset());
  }

  public static void assertSourcePosition(final XStackFrame frame, VirtualFile file, int line) {
    XSourcePosition position = frame.getSourcePosition();
    assertNotNull(position);
    assertEquals(file, position.getFile());
    assertEquals(line, position.getLine());
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
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    Ref<XBreakpoint> breakpoint = Ref.create(null);
    XBreakpointUtil.breakpointTypes().select(exceptionType).findFirst().ifPresent(type ->
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          breakpoint.set(breakpointManager.addBreakpoint(type, properties));
        }
      }.execute()
    );
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
    return ReadAction.compute(breakpointManager::getAllBreakpoints);
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
  
  public static class XTestExecutionStackContainer extends XTestContainer<XExecutionStack> implements XSuspendContext.XExecutionStackContainer {
    @Override
    public void errorOccurred(@NotNull String errorMessage) {
      setErrorMessage(errorMessage);
    }

    @Override
    public void addExecutionStack(@NotNull List<? extends XExecutionStack> executionStacks, boolean last) {
      addChildren(executionStacks, last);
    }
  } 

  public static class XTestStackFrameContainer extends XTestContainer<XStackFrame> implements XStackFrameContainerEx {
    public volatile XStackFrame frameToSelect;
    
    public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
      addChildren(stackFrames, last);
    }

    @Override
    public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, @Nullable XStackFrame toSelect, boolean last) {
      if (toSelect != null) frameToSelect = toSelect;
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
