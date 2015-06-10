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
package org.jetbrains.debugger;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import com.intellij.util.io.socketConnection.SocketConnectionListener;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.connection.VmConnection;
import org.jetbrains.debugger.frame.SuspendContextImpl;

import javax.swing.event.HyperlinkListener;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DebugProcessImpl<C extends VmConnection> extends XDebugProcess {
  protected final AtomicBoolean repeatStepInto = new AtomicBoolean();
  protected volatile StepAction lastStep;
  protected volatile CallFrame lastCallFrame;
  protected volatile boolean isForceStep;
  protected volatile boolean disableDoNotStepIntoLibraries;

  protected final ConcurrentMap<Url, VirtualFile> urlToFileCache = ContainerUtil.newConcurrentMap();

  protected final C connection;

  private boolean processBreakpointConditionsAtIdeSide;

  private final XDebuggerEditorsProvider editorsProvider;
  private final XSmartStepIntoHandler<?> smartStepIntoHandler;
  protected XBreakpointHandler<?>[] breakpointHandlers;

  protected final ExecutionResult executionResult;

  protected DebugProcessImpl(@NotNull XDebugSession session, @NotNull C connection,
                             @NotNull XDebuggerEditorsProvider editorsProvider,
                             @Nullable XSmartStepIntoHandler<?> smartStepIntoHandler,
                             @Nullable ExecutionResult executionResult) {
    super(session);

    this.executionResult = executionResult;
    this.connection = connection;
    this.editorsProvider = editorsProvider;
    this.smartStepIntoHandler = smartStepIntoHandler;

    connection.addListener(new SocketConnectionListener() {
      @Override
      public void statusChanged(@NotNull ConnectionStatus status) {
        if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.DETACHED) {
          if (status == ConnectionStatus.DETACHED) {
            if (getRealProcessHandler() != null) {
              // here must we must use effective process handler
              getProcessHandler().detachProcess();
            }
          }
          getSession().stop();
        }
        else {
          getSession().rebuildViews();
        }
      }
    });
  }

  @Nullable
  protected final ProcessHandler getRealProcessHandler() {
    return executionResult == null ? null : executionResult.getProcessHandler();
  }

  @NotNull
  public final C getConnection() {
    return connection;
  }

  @Override
  @Nullable
  public final XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return smartStepIntoHandler;
  }

  @NotNull
  @Override
  public final XBreakpointHandler<?>[] getBreakpointHandlers() {
    return breakpointHandlers;
  }

  @Override
  @NotNull
  public final XDebuggerEditorsProvider getEditorsProvider() {
    return editorsProvider;
  }

  public void setProcessBreakpointConditionsAtIdeSide(boolean processBreakpointConditionsAtIdeSide) {
    this.processBreakpointConditionsAtIdeSide = processBreakpointConditionsAtIdeSide;
  }

  public final Vm getVm() {
    return connection.getVm();
  }

  private void updateLastCallFrame() {
    Vm vm = getVm();
    if (vm != null) {
      SuspendContext context = vm.getSuspendContextManager().getContext();
      if (context != null) {
        lastCallFrame = context.getTopFrame();
        return;
      }
    }

    lastCallFrame = null;
  }

  @Override
  public boolean checkCanPerformCommands() {
    return getVm() != null;
  }

  @Override
  public boolean isValuesCustomSorted() {
    return true;
  }

  @Override
  public void startStepOver() {
    updateLastCallFrame();
    continueVm(StepAction.OVER);
  }

  @Override
  public void startForceStepInto() {
    isForceStep = true;
    startStepInto();
  }

  @Override
  public void startStepInto() {
    updateLastCallFrame();
    continueVm(StepAction.IN);
  }

  @Override
  public void startStepOut() {
    if (isVmStepOutCorrect()) {
      lastCallFrame = null;
    }
    else {
      updateLastCallFrame();
    }
    continueVm(StepAction.OUT);
  }

  // some VM (firefox for example) doesn't implement step out correctly, so, we need to fix it
  protected boolean isVmStepOutCorrect() {
    return true;
  }

  protected void continueVm(@NotNull StepAction stepAction) {
    SuspendContextManager suspendContextManager = getVm().getSuspendContextManager();
    if (stepAction == StepAction.CONTINUE) {
      if (suspendContextManager.getContext() == null) {
        // on resumed we ask session to resume, and session then call our "resume", but we have already resumed, so, we don't need to send "continue" message
        return;
      }

      lastStep = null;
      lastCallFrame = null;
      urlToFileCache.clear();
      disableDoNotStepIntoLibraries = false;
    }
    else {
      lastStep = stepAction;
    }
    suspendContextManager.continueVm(stepAction, 1);
  }

  protected final void setOverlay() {
    getVm().getSuspendContextManager().setOverlayMessage("Paused in debugger");
  }

  protected final void processBreakpoint(@NotNull final SuspendContext suspendContext,
                                         @NotNull final XBreakpoint<?> breakpoint,
                                         @NotNull final SuspendContextImpl xSuspendContext) {
    XExpression conditionExpression = breakpoint.getConditionExpression();
    String condition = conditionExpression == null ? null : conditionExpression.getExpression();
    if (!processBreakpointConditionsAtIdeSide || condition == null) {
      processBreakpointLogExpressionAndSuspend(breakpoint, xSuspendContext, suspendContext);
    }
    else {
      xSuspendContext.evaluateExpression(condition).done(new ContextDependentAsyncResultConsumer<String>(suspendContext) {
        @Override
        public void consume(String evaluationResult, @NotNull Vm vm) {
          if ("false".equals(evaluationResult)) {
            resume();
          }
          else {
            processBreakpointLogExpressionAndSuspend(breakpoint, xSuspendContext, suspendContext);
          }
        }
      }).rejected(new ContextDependentAsyncResultConsumer<Throwable>(suspendContext) {
        @Override
        public void consume(Throwable failure, @NotNull Vm vm) {
          processBreakpointLogExpressionAndSuspend(breakpoint, xSuspendContext, suspendContext);
        }
      });
    }
  }

  private void processBreakpointLogExpressionAndSuspend(@NotNull final XBreakpoint<?> breakpoint,
                                                        @NotNull final SuspendContextImpl xSuspendContext,
                                                        @NotNull SuspendContext suspendContext) {
    XExpression logExpressionObject = breakpoint.getLogExpressionObject();
    final String logExpression = logExpressionObject == null ? null : logExpressionObject.getExpression();

    if (logExpression == null) {
      breakpointReached(breakpoint, null, xSuspendContext);
    }
    else {
      xSuspendContext.evaluateExpression(logExpression).done(new ContextDependentAsyncResultConsumer<String>(suspendContext) {
        @Override
        public void consume(String logResult, @NotNull Vm vm) {
          breakpointReached(breakpoint, logResult, xSuspendContext);
        }
      }).rejected(new ContextDependentAsyncResultConsumer<Throwable>(suspendContext) {
        @Override
        public void consume(Throwable logResult, @NotNull Vm vm) {
          breakpointReached(breakpoint, "Failed to evaluate expression: " + logExpression, xSuspendContext);
        }
      });
    }
  }

  private void breakpointReached(@NotNull XBreakpoint<?> breakpoint,
                                 @Nullable String evaluatedLogExpression,
                                 @NotNull XSuspendContext suspendContext) {
    if (getSession().breakpointReached(breakpoint, evaluatedLogExpression, suspendContext)) {
      setOverlay();
    }
    else {
      resume();
    }
  }

  @Override
  public final void startPausing() {
    connection.getVm().getSuspendContextManager().suspend().rejected(new RejectErrorReporter(getSession(), "Cannot pause"));
  }

  @Override
  public final String getCurrentStateMessage() {
    return connection.getState().getMessage();
  }

  @Nullable
  @Override
  public final HyperlinkListener getCurrentStateHyperlinkListener() {
    return getConnection().getState().getMessageLinkListener();
  }

  @Override
  @NotNull
  protected ProcessHandler doGetProcessHandler() {
    return executionResult == null ? new SilentDestroyDebugProcessHandler() : executionResult.getProcessHandler();
  }

  private static final class SilentDestroyDebugProcessHandler extends DefaultDebugProcessHandler {
    @Override
    public boolean isSilentlyDestroyOnClose() {
      return true;
    }
  }

  public void saveResolvedFile(@NotNull Url url, @NotNull VirtualFile file) {
    urlToFileCache.putIfAbsent(url, file);
  }
}