// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static junit.framework.TestCase.*;

public interface DebuggerMethods extends CompilerMethods {
  Logger getLogger();

  @Nullable
  default DebugProcessImpl getDebugProcess() {
    final DebuggerSession session = getDebugSession();
    return (session == null ? null : session.getProcess());
  }

  @Nullable
  default DebuggerSession getDebugSession() {
    return DebuggerManagerEx.getInstanceEx(getProject()).getContext().getDebuggerSession();
  }

  default void runDebugger(final RunProfile configuration, Runnable cl) throws ExecutionException {
    EdtTestUtil.runInEdtAndWait(
      () -> runConfiguration(DefaultDebugExecutor.class, new ProcessListener() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          if (outputType.equals(ProcessOutputTypes.STDERR)) {
            System.out.println(event.getText());
          }
        }
      }, configuration, null)
    );
    getLogger().debug("after start");
    try {
      cl.run();
    }
    catch (Throwable t) {
      //noinspection CallToPrintStackTrace
      t.printStackTrace();
      throw t;
    }
    finally {
      final DebugProcessImpl process = getDebugProcess();
      ProcessHandler handler = (process == null ? null : process.getProcessHandler());
      resume();
      if (handler != null && !handler.waitFor(ourTimeout)) {
        if (handler instanceof OSProcessHandler osProcessHandler) {
          OSProcessUtil.killProcessTree(osProcessHandler.getProcess());
        }
        else {
          System.out.println("can't terminate " + handler);
        }

        //noinspection ThrowFromFinallyBlock
        throw new AssertionError("too long waiting for process termination");
      }
    }
  }

  default void addBreakpoint(final VirtualFile file, final int line) {
    EdtTestUtil.runInEdtAndWait(() -> DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager()
      .addLineBreakpoint(FileDocumentManager.getInstance().getDocument(file), line));
  }

  default SuspendContextImpl waitForBreakpoint() throws InterruptedException {
    getLogger().debug("waitForBreakpoint");
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final DebugProcessImpl process = getDebugProcess();
    // wait for all events processed
    process.getManagerThread().invoke(PrioritizedTask.Priority.NORMAL, () -> semaphore.up());

    assertTrue("Too long debugger actions", semaphore.waitFor(ourTimeout));

    int i = 0;
    SuspendManager suspendManager = process.getSuspendManager();
    while (i++ < ourTimeout / 10 &&
           suspendManager.getPausedContext() == null &&
           !process.getProcessHandler().isProcessTerminated()) {
      Thread.sleep(10);
    }
    SuspendContextImpl context = suspendManager.getPausedContext();
    assertNotNull("too long process, terminated=" + process.getProcessHandler().isProcessTerminated(), context);
    return context;
  }

  default void resume() {
    if (getDebugSession() == null) return;

    getDebugProcess().getManagerThread()
      .invoke(getDebugProcess().createResumeCommand(getDebugProcess().getSuspendManager().getPausedContext()));
  }

  default SourcePosition getSourcePosition() {
    final EvaluationContextImpl context = evaluationContext();
    Computable<SourcePosition> a = () -> ContextUtil.getSourcePosition(context);
    return ApplicationManager.getApplication().runReadAction(a);
  }

  default EvaluationContextImpl evaluationContext() {
    final SuspendContextImpl suspendContext = getDebugProcess().getSuspendManager().getPausedContext();
    return new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
  }

  default void eval(final String codeText, String expected) {
    eval(codeText, expected, null);
  }

  default void eval(final String codeText, String expected, FileType fileType) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicReference<EvaluationContextImpl> ctx = new AtomicReference<>();
    final WatchItemDescriptor item = new WatchItemDescriptor(getProject(),
                                                             new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText, "", fileType));
    managed(() -> {
      ctx.set(evaluationContext());
      item.setContext(ctx.get());
      item.updateRepresentation(ctx.get(), () -> {
      });
      semaphore.up();
      return null;
    });
    assert semaphore.waitFor(ourTimeout) : "too long evaluation: " + item.getLabel() + " " + item.getEvaluateException();

    String result = managed(() -> {
      try {
        EvaluateException e = item.getEvaluateException();
        if (e != null) return ExceptionUtil.getThrowableText(e);
        return DebuggerUtils.getValueAsString(ctx.get(), item.getValue());
      }
      catch (EvaluateException ex) {
        return ExceptionUtil.getThrowableText(ex);
      }
    });
    assertEquals(expected, result);
  }

  default <T> T managed(Supplier<T> cl) {
    DebuggerContextImpl ctx = DebuggerContextUtil.createDebuggerContext(getDebugSession(),
                                                                        getDebugProcess().getSuspendManager().getPausedContext());
    ManagedCommand<T> command = new ManagedCommand<>(ctx, cl);
    getDebugProcess().getManagerThread().invoke(command);
    boolean finished = command.getSemaphore().waitFor(ourTimeout);
    assertTrue("Too long debugger action", finished);
    return command.getResult();
  }

  int ourTimeout = 60000;

  class ManagedCommand<T> extends DebuggerContextCommandImpl {
    public ManagedCommand(@NotNull DebuggerContextImpl debuggerContext, Supplier<? extends T> action) {
      super(debuggerContext);
      mySemaphore = new Semaphore();
      mySemaphore.down();
      myAction = action;
    }

    public Semaphore getSemaphore() {
      return mySemaphore;
    }

    public T getResult() {
      return myResult;
    }

    @Override
    public void threadAction(@NotNull SuspendContextImpl suspendContext) {
      try {
        myResult = myAction.get();
      }
      finally {
        mySemaphore.up();
      }
    }

    @Override
    protected void commandCancelled() {
      System.out.println(DebugUtil.currentStackTrace());
    }

    private final Semaphore mySemaphore;
    private final Supplier<? extends T> myAction;
    private T myResult;
  }
}