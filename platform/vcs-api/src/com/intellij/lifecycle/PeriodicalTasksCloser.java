/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.Rethrow;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeriodicalTasksCloser extends ProjectManagerAdapter implements ProjectLifecycleListener, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lifecycle.PeriodicalTasksCloser");
  private final Object myLock = new Object();

  PeriodicalTasksCloser(final ProjectManager projectManager) {
    Application application = ApplicationManager.getApplication();
    MessageBusConnection connection = application.getMessageBus().connect(application);
    connection.subscribe(ProjectLifecycleListener.TOPIC, this);
    projectManager.addProjectManagerListener(this, application);
  }

  public static PeriodicalTasksCloser getInstance() {
    return ApplicationManager.getApplication().getComponent(PeriodicalTasksCloser.class);
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return PeriodicalTasksCloser.class.getName();
  }

  @Override
  public void initComponent() {
  }

  private static class Interrupter implements Disposable {
    private final String myName;
    private final Runnable myRunnable;

    private Interrupter(@NotNull String name, @NotNull Runnable runnable) {
      myName = name;
      myRunnable = runnable;
    }

    @Override
    public void dispose() {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setText(myName);
        indicator.checkCanceled();
      }
      myRunnable.run();
    }
  }

  private static final Key<List<Interrupter>> INTERRUPTERS = Key.create("VCS_INTERRUPTERS");
  public boolean register(@NotNull Project project, @NotNull String name, @NotNull Runnable runnable) {
    synchronized (myLock) {
      if (project.isDisposed()) {
        return false;
      }

      Interrupter interrupter = new Interrupter(name, runnable);
      List<Interrupter> list = project.getUserData(INTERRUPTERS);
      if (list == null) {
        list = new ArrayList<Interrupter>();
        project.putUserData(INTERRUPTERS, list);
      }
      list.add(interrupter);
      Disposer.register(project, interrupter);

      return true;
    }
  }

  public boolean canCloseProject(Project project) {
    return true;
  }

  public void projectClosing(final Project project) {
    final List<Interrupter> interrupters;
    synchronized (myLock) {
      List<Interrupter> list = project.getUserData(INTERRUPTERS);
      if (list == null) {
        interrupters = null;
      }
      else {
        interrupters = new ArrayList<Interrupter>(list);
        list.clear();
      }
    }
    if (interrupters != null) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (Interrupter interrupter : interrupters) {
            Disposer.dispose(interrupter);
          }
        }
      }, "Please wait for safe shutdown of periodical tasks...", true, project);
    }
  }

  @Override
  public void afterProjectClosed(@NotNull Project project) {
  }

  @Override
  public void projectComponentsInitialized(Project project) {
  }

  @Override
  public void beforeProjectLoaded(@NotNull Project project) {
  }

  public <T> T safeGetComponent(@NotNull final Project project, final Class<T> componentClass) throws ProcessCanceledException {
    T component = null;
    try {
      component = project.getComponent(componentClass);
    }
    catch (NullPointerException e) {
      throwCanceledException(project, e);
    } catch (AssertionError e) {
      throwCanceledException(project, e);
    }
    if (component == null) {
      throwCanceledException(project, new NullPointerException());
    }
    return component;
  }

  public <T> T safeGetService(@NotNull final Project project, final Class<T> componentClass) throws ProcessCanceledException {
    try {
      return ServiceManager.getService(project, componentClass);
    }
    catch (NullPointerException e) {
      throwCanceledException(project, e);
    } catch (AssertionError e) {
      throwCanceledException(project, e);
    }
    return null;
  }

  private void throwCanceledException(final Project project, final Throwable t) {
    synchronized (myLock) {
      // allow NPE & assertion _catch_ only if project is closed and being disposed
      if (project.isOpen()) {
        Rethrow.reThrowRuntime(t);
      }
    }
    throw new ProcessCanceledException();
  }

  public void invokeAndWaitInterruptedWhenClosing(Project project, @NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    final AtomicBoolean start = new AtomicBoolean(true);
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    Runnable runnable1 = new Runnable() {
      public void run() {
        try {
          runnable.run();
        }
        finally {
          semaphore.up();
        }
      }

      @NonNls
      public String toString() {
        return "PeriodicalTaskCloser's invoke and wait [" + runnable.toString() + "]";
      }
    };
    ApplicationManager.getApplication().invokeLater(runnable1, modalityState, new Condition<Object>() {
      public boolean value(Object o) {
        return !start.get();
      }
    });

    while (true) {
      if (semaphore.waitFor(1000)) {
        return;
      }
      if (project != null && !project.isOpen()) {
        start.set(false);
      }
    }
  }
}
