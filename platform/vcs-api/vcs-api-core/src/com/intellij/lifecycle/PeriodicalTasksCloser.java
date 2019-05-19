// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lifecycle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link ServiceManager#getService(Project, Class)} and {@link Project#getComponent(Class)}.
 * <br/><br/>
 * To avoid "Already Disposed" exceptions and NPEs the calls to getService/getComponent should happen either from a Read Action
 * with a dispose check, or from a background task with a proper dispose-aware ProgressIndicator,
 * e.g. via {@link ProgressManager#run(Task)} or {@code BackgroundTaskUtil#executeOnPooledThread}.
 */
@Deprecated
public class PeriodicalTasksCloser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lifecycle.PeriodicalTasksCloser");
  private final Object myLock = new Object();

  public static PeriodicalTasksCloser getInstance() {
    return ServiceManager.getService(PeriodicalTasksCloser.class);
  }

  public <T> T safeGetComponent(@NotNull final Project project, final Class<T> componentClass) throws ProcessCanceledException {
    T component = null;
    try {
      component = project.getComponent(componentClass);
    }
    catch (NullPointerException | AssertionError e) {
      throwCanceledException(project, e);
    }
    if (component == null) {
      if (project.isDefault()) {
        LOG.info("no component in default project: " + componentClass.getName());
      }
      throwCanceledException(project, new NullPointerException());
    }
    return component;
  }

  public <T> T safeGetService(@NotNull final Project project, final Class<T> componentClass) throws ProcessCanceledException {
    try {
      T service = ServiceManager.getService(project, componentClass);
      if (service == null) {
        ProgressManager.checkCanceled();
        if (project.isDefault()) {
          LOG.info("no service in default project: " + componentClass.getName());
        }
      }
      return service;
    }
    catch (NullPointerException | AssertionError e) {
      throwCanceledException(project, e);
    }
    return null;
  }

  private void throwCanceledException(final Project project, final Throwable t) {
    synchronized (myLock) {
      // allow NPE & assertion _catch_ only if project is closed and being disposed
      if (project.isOpen()) {
        ExceptionUtil.rethrowAllAsUnchecked(t);
      }
    }
    throw new ProcessCanceledException();
  }
}
