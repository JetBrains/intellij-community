// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.remoteServer.runtime.log.TerminalHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeploymentLogManagerImpl implements DeploymentLogManager {
  private final LoggingHandlerImpl myMainLoggingHandler;
  private final Project myProject;
  private final List<LoggingHandlerBase> myAdditionalLoggingHandlers = new ArrayList<>();
  private final Runnable myChangeListener;

  private final AtomicBoolean myLogsDisposed = new AtomicBoolean(false);
  private final Disposable myLogsDisposable;
  private boolean myMainHandlerVisible = false;

  public DeploymentLogManagerImpl(@NotNull Project project, @NotNull Runnable changeListener) {
    myProject = project;
    myChangeListener = changeListener;
    myMainLoggingHandler = new LoggingHandlerImpl.Colored(null, project);
    myLogsDisposable = Disposer.newDisposable();
    Disposer.register(myLogsDisposable, myMainLoggingHandler);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        disposeLogs();
      }
    });
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  public DeploymentLogManagerImpl withMainHandlerVisible(boolean mainHandlerVisible) {
    myMainHandlerVisible = mainHandlerVisible;
    return this;
  }

  public boolean isMainHandlerVisible() {
    return myMainHandlerVisible;
  }

  @Override
  public @NotNull LoggingHandlerImpl getMainLoggingHandler() {
    return myMainLoggingHandler;
  }

  @Override
  public @NotNull LoggingHandler addAdditionalLog(@NotNull String presentableName) {
    synchronized (myLogsDisposed) {
      if (myLogsDisposed.get()) {
        throw new IllegalStateException("Already disposed, can't add " + presentableName);
      }

      LoggingHandlerImpl handler = new LoggingHandlerImpl.Colored(presentableName, myProject);
      addAdditionalLoggingHandler(handler);
      return handler;
    }
  }

  @Override
  public void removeAdditionalLog(@NotNull String presentableName) {
    synchronized (myAdditionalLoggingHandlers) {
      myAdditionalLoggingHandlers.removeIf(next -> presentableName.equals(next.getPresentableName()));
    }
    myChangeListener.run();
  }

  public @NotNull LoggingHandler findOrCreateAdditionalLog(@NotNull String presentableName) {
    synchronized (myAdditionalLoggingHandlers) {
      for (LoggingHandlerBase next : myAdditionalLoggingHandlers) {
        if (next instanceof LoggingHandler && presentableName.equals(next.getPresentableName())) {
          return (LoggingHandler)next;
        }
      }
      return addAdditionalLog(presentableName);
    }
  }

  @Override
  public @Nullable TerminalHandler addTerminal(final @NotNull @Nls String presentableName, InputStream terminalOutput, OutputStream terminalInput) {
    synchronized (myLogsDisposed) {
      if (myLogsDisposed.get()) {
        return null;
      }
      TerminalHandlerBase handler = CloudTerminalProvider.getInstance().createTerminal(presentableName, myProject, terminalOutput,
                                                                                       terminalInput);
      addAdditionalLoggingHandler(handler);
      return handler;
    }
  }
  //
  //private static CloudTerminalProvider getTerminalProvider() {
  //  CloudTerminalProvider.getInstance()
  //
  //  CloudTerminalProvider terminalProvider = ArrayUtil.getFirstElement(CloudTerminalProvider.EP_NAME.getExtensions());
  //  return terminalProvider != null ? terminalProvider : ConsoleTerminalHandlerImpl.PROVIDER;
  //}

  @Override
  public boolean isTtySupported() {
    return CloudTerminalProvider.getInstance().isTtySupported();
  }

  private void addAdditionalLoggingHandler(LoggingHandlerBase loggingHandler) {
    Disposer.register(myLogsDisposable, loggingHandler);
    synchronized (myAdditionalLoggingHandlers) {
      myAdditionalLoggingHandlers.add(loggingHandler);
    }
    myChangeListener.run();
  }

  /*
    FIXME: memory leak. We need to remove closed handlers
   */
  public @NotNull List<LoggingHandlerBase> getAdditionalLoggingHandlers() {
    List<LoggingHandlerBase> result;
    synchronized (myAdditionalLoggingHandlers) {
      result = new ArrayList<>(myAdditionalLoggingHandlers);
    }
    return result;
  }

  public void disposeLogs() {
    synchronized (myLogsDisposed) {
      if (!myLogsDisposed.getAndSet(true)) {
        Disposer.dispose(myLogsDisposable);
      }
    }
  }
}
