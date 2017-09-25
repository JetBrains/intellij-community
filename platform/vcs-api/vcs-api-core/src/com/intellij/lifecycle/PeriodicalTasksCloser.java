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
package com.intellij.lifecycle;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

public class PeriodicalTasksCloser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lifecycle.PeriodicalTasksCloser");
  private final Object myLock = new Object();

  public static PeriodicalTasksCloser getInstance() {
    return ApplicationManager.getApplication().getComponent(PeriodicalTasksCloser.class);
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
