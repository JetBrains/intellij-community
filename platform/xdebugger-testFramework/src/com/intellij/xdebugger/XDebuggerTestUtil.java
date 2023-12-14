// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.Predicates.nonNull;
import static org.junit.Assert.assertNotNull;

@TestOnly
public class XDebuggerTestUtil {
  public static final int TIMEOUT_MS = 25_000;

  XDebuggerTestUtil() {
  }

  public static List<? extends XLineBreakpointType.XLineBreakpointVariant>
  computeLineBreakpointVariants(Project project, VirtualFile file, int line) {
    return computeLineBreakpointVariants(project, file, line, 0);
  }

  public static List<? extends XLineBreakpointType.XLineBreakpointVariant>
  computeLineBreakpointVariants(Project project, VirtualFile file, int line, int column) {
    return ReadAction.compute(() -> {
      List<XLineBreakpointType> types = StreamEx.of(XDebuggerUtil.getInstance().getLineBreakpointTypes())
                                                .filter(type -> type.canPutAt(file, line, project))
                                                .collect(Collectors.toCollection(SmartList::new));
      return XDebuggerUtilImpl.getLineBreakpointVariantsSync(project, types, XSourcePositionImpl.create(file, line, column));
    });
  }

  @Nullable
  public static XLineBreakpoint toggleBreakpoint(Project project, VirtualFile file, int line) {
    final XDebuggerUtilImpl debuggerUtil = (XDebuggerUtilImpl)XDebuggerUtil.getInstance();
    final Promise<XLineBreakpoint> breakpointPromise = WriteAction.computeAndWait(
      () -> debuggerUtil.toggleAndReturnLineBreakpoint(project, file, line, false));
    try {
      return breakpointPromise.blockingGet(TIMEOUT_MS);
    }
    catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  public static <P extends XBreakpointProperties> XBreakpoint<P> insertBreakpoint(final Project project,
                                                                                  final P properties,
                                                                                  final Class<? extends XBreakpointType<XBreakpoint<P>, P>> typeClass) {
    return XDebuggerManager.getInstance(project).getBreakpointManager()
      .addBreakpoint(XBreakpointType.EXTENSION_POINT_NAME.findExtension(typeClass), properties);
  }

  public static void removeBreakpoint(@NotNull final Project project,
                                      @NotNull final VirtualFile file,
                                      final int line) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    WriteAction.runAndWait(() -> {
      XLineBreakpoint<?> breakpoint = Arrays.stream(XDebuggerUtil.getInstance().getLineBreakpointTypes())
        .map(t -> breakpointManager.findBreakpointAtLine(t, file, line))
        .filter(nonNull())
        .findFirst().orElse(null);
      assertNotNull(breakpoint);
      breakpointManager.removeBreakpoint(breakpoint);
    });
  }

  public static @Nullable XExecutionStack getActiveThread(@NotNull XDebugSession session) {
    return session.getSuspendContext().getActiveExecutionStack();
  }

  public static List<XExecutionStack> collectThreads(@NotNull XDebugSession session) {
    return collectThreads(session, TIMEOUT_MS);
  }

  public static List<XExecutionStack> collectThreads(@NotNull XDebugSession session, int timeoutMs) {
    return collectThreadsWithErrors(session, timeoutMs).first;
  }

  public static Pair<List<XExecutionStack>, String> collectThreadsWithErrors(@NotNull XDebugSession session) {
    return collectThreadsWithErrors(session, TIMEOUT_MS);
  }

  public static Pair<List<XExecutionStack>, String> collectThreadsWithErrors(@NotNull XDebugSession session, int timeoutMs) {
    XTestExecutionStackContainer container = new XTestExecutionStackContainer();
    session.getSuspendContext().computeExecutionStacks(container);
    return container.waitFor(timeoutMs);
  }

  public static List<XStackFrame> collectFrames(@NotNull XDebugSession session) {
    return collectFrames(null, session);
  }

  public static List<XStackFrame> collectFrames(@Nullable XExecutionStack thread, @NotNull XDebugSession session) {
    return collectFrames(thread == null ? Objects.requireNonNull(getActiveThread(session)) : thread);
  }

  public static String getFramePresentation(XStackFrame frame) {
    TextTransferable.ColoredStringBuilder builder = new TextTransferable.ColoredStringBuilder();
    frame.customizePresentation(builder);
    return builder.getBuilder().toString();
  }

  public static List<XStackFrame> collectFrames(@NotNull XExecutionStack thread) {
    return collectFrames(thread, TIMEOUT_MS * 2);
  }

  public static List<XStackFrame> collectFrames(XExecutionStack thread, long timeout) {
    return collectFramesWithError(thread, timeout).first;
  }

  public static Pair<List<XStackFrame>, String> collectFramesWithError(XExecutionStack thread, long timeout) {
    XTestStackFrameContainer container = new XTestStackFrameContainer();
    thread.computeStackFrames(0, container);
    return container.waitFor(timeout);
  }

  public static Pair<List<XStackFrame>, XStackFrame> collectFramesWithSelected(@NotNull XDebugSession session, long timeout) {
    return collectFramesWithSelected(Objects.requireNonNull(getActiveThread(session)), timeout);
  }

  public static Pair<List<XStackFrame>, XStackFrame> collectFramesWithSelected(XExecutionStack thread, long timeout) {
    XTestStackFrameContainer container = new XTestStackFrameContainer();
    thread.computeStackFrames(0, container);
    List<XStackFrame> all = container.waitFor(timeout).first;
    return Pair.create(all, container.frameToSelect);
  }

  public static XStackFrame getFrameAt(@NotNull XDebugSession session, int frameIndex) {
    return getFrameAt(Objects.requireNonNull(getActiveThread(session)), frameIndex);
  }

  public static XStackFrame getFrameAt(@NotNull XExecutionStack thread, int frameIndex) {
    return frameIndex == 0 ? thread.getTopFrame() : collectFrames(thread).get(frameIndex);
  }

  @NotNull
  public static List<XValue> collectChildren(XValueContainer value) {
    return new XTestCompositeNode(value).collectChildren();
  }

  @NotNull
  public static Pair<List<XValue>, String> collectChildrenWithError(XValueContainer value) {
    return new XTestCompositeNode(value).collectChildrenWithError();
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

  public static void waitForSwing() throws InterruptedException {
    final com.intellij.util.concurrency.Semaphore s = new com.intellij.util.concurrency.Semaphore();
    s.down();
    ApplicationManager.getApplication().invokeLater(() -> s.up());
    s.waitForUnsafe();
    UIUtil.invokeAndWaitIfNeeded(() -> {});
  }

  @NotNull
  public static XValue findVar(Collection<? extends XValue> vars, String name) {
    StringBuilder names = new StringBuilder();
    for (XValue each : vars) {
      if (each instanceof XNamedValue) {
        String eachName = ((XNamedValue)each).getName();
        if (eachName.equals(name)) return each;

        if (!names.isEmpty()) names.append(", ");
        names.append(eachName);
      }
    }
    throw new AssertionError("var '" + name + "' not found among " + names);
  }

  public static XTestValueNode computePresentation(@NotNull XValue value) {
    return computePresentation(value, TIMEOUT_MS);
  }

  public static XTestValueNode computePresentation(XValue value, long timeout) {
    XTestValueNode node = new XTestValueNode();
    if (value instanceof XNamedValue) {
      node.myName = ((XNamedValue)value).getName();
    }
    value.computePresentation(node, XValuePlace.TREE);
    node.waitFor(timeout);
    return node;
  }

  public static <T> @Nullable T waitFor(@NotNull Future<T> future, long timeoutInMillis) {
    return waitFor(remaining -> {
      try {
        return future.get(remaining, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException e) {
        return null;
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause != null) {
          ExceptionUtil.rethrow(cause);
        }
        throw new RuntimeException(e);
      }
    }, timeoutInMillis);
  }

  public static boolean waitFor(@NotNull Semaphore semaphore, long timeoutInMillis) {
    return waitFor(remaining -> semaphore.tryAcquire(remaining, TimeUnit.MILLISECONDS) ? Boolean.TRUE : null,
                   timeoutInMillis) == Boolean.TRUE;
  }

  private static <T> @Nullable T waitFor(@NotNull ThrowableConvertor<? super Long, T, ? extends InterruptedException> waitFunction,
                                         long timeoutInMillis) {
    long end = System.currentTimeMillis() + timeoutInMillis;
    flushEventQueue();
    for (long remaining = timeoutInMillis; remaining > 0; remaining = end - System.currentTimeMillis()) {
      try {
        // 10ms is the sleep interval used by ProgressIndicatorUtils for busy-waiting.
        T result = waitFunction.convert(Math.min(10, remaining));
        if (result != null) {
          return result;
        }
      }
      catch (InterruptedException ignored) {
      }
      finally {
        flushEventQueue();
      }
    }
    return null;
  }

  public static void markValue(XValueMarkers<?, ?> markers, @NotNull XValue value, @NotNull ValueMarkup markup) {
    try {
      markers.markValue(value, markup).blockingGet(TIMEOUT_MS);
    }
    catch (TimeoutException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  // Rider needs this in order to be able to receive messages from the backend when waiting on the EDT.
  private static void flushEventQueue() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
  }

  @NotNull
  public static String getConsoleText(final @NotNull ConsoleViewImpl consoleView) {
    WriteAction.runAndWait(() -> consoleView.flushDeferredText());

    return consoleView.getEditor().getDocument().getText();
  }

  public static <T extends XBreakpointType> XBreakpoint addBreakpoint(@NotNull final Project project,
                                                                      @NotNull final Class<T> exceptionType,
                                                                      @NotNull final XBreakpointProperties properties) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    Ref<XBreakpoint> breakpoint = Ref.create(null);
    XBreakpointUtil.breakpointTypes()
                   .select(exceptionType)
                   .findFirst()
                   .ifPresent(type -> breakpoint.set(breakpointManager.addBreakpoint(type, properties)));
    return breakpoint.get();
  }

  public static void removeAllBreakpoints(@NotNull Project project) {
    XDebuggerUtilImpl.removeAllBreakpoints(project);
  }

  public static XBreakpoint<?>[] getBreakpoints(final XBreakpointManager breakpointManager) {
    return breakpointManager.getAllBreakpoints();
  }

  public static <B extends XBreakpoint<?>>
  void setDefaultBreakpointEnabled(@NotNull final Project project, Class<? extends XBreakpointType<B, ?>> bpTypeClass, boolean enabled) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XBreakpointType<B, ?> bpType = XDebuggerUtil.getInstance().findBreakpointType(bpTypeClass);
    Set<B> defaultBreakpoints = breakpointManager.getDefaultBreakpoints(bpType);
    for (B defaultBreakpoint : defaultBreakpoints) {
      defaultBreakpoint.setEnabled(enabled);
    }
  }

  public static void setBreakpointCondition(Project project, int line, final String condition) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : getBreakpoints(breakpointManager)) {
      if (breakpoint instanceof XLineBreakpoint lineBreakpoint) {

        if (lineBreakpoint.getLine() == line) {
          WriteAction.runAndWait(() -> lineBreakpoint.setCondition(condition));
        }
      }
    }
  }

  public static void setBreakpointLogExpression(Project project, int line, final String logExpression) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpoint breakpoint : getBreakpoints(breakpointManager)) {
      if (breakpoint instanceof XLineBreakpoint lineBreakpoint) {

        if (lineBreakpoint.getLine() == line) {
          WriteAction.runAndWait(() -> {
            lineBreakpoint.setLogExpression(logExpression);
            lineBreakpoint.setLogMessage(true);
          });
        }
      }
    }
  }

  public static void disposeDebugSession(final XDebugSession debugSession) {
    WriteAction.runAndWait(() -> {
      XDebugSessionImpl session = (XDebugSessionImpl)debugSession;
      Disposer.dispose(session.getSessionTab());
      Disposer.dispose(session.getConsoleView());
    });
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

    @Override
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
