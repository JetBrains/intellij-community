/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeriodicalTasksCloser implements ProjectManagerListener {
  private final static Logger LOG = Logger.getInstance("#com.intellij.lifecycle.PeriodicalTasksCloser");
  private final static Object ourLock = new Object();
  private final List<Pair<String, Runnable>> myInterrupters;
  private final static Map<Project, Boolean> myStates = new HashMap<Project, Boolean>();
  private final Project myProject;

  private PeriodicalTasksCloser(final Project project, final ProjectManager projectManager) {
    myProject = project;
    myInterrupters = new ArrayList<Pair<String, Runnable>>();
    projectManager.addProjectManagerListener(project, this);
  }

  public static PeriodicalTasksCloser getInstance(final Project project) {
    return ServiceManager.getService(project, PeriodicalTasksCloser.class);
  }

  public boolean register(final String name, final Runnable runnable) {
    synchronized (ourLock) {
      if (Boolean.FALSE.equals(myStates.get(myProject))) {
        return false;
      }
      myInterrupters.add(new Pair<String, Runnable>(name, runnable));
      return true;
    }
  }

  public void projectOpened(Project project) {
    synchronized (ourLock) {
      myStates.put(project, Boolean.TRUE);
    }
  }

  public boolean canCloseProject(Project project) {
    return true;
  }

  public void projectClosed(Project project) {
    synchronized (ourLock) {
      myStates.remove(project);
    }
  }

  public void projectClosing(Project project) {
    synchronized (ourLock) {
      myStates.put(project, Boolean.FALSE);
    }
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        final List<Pair<String, Runnable>> list;
        synchronized (ourLock) {
          list = myInterrupters;
        }
        for (Pair<String, Runnable> pair : list) {
          if (indicator != null) {
            indicator.setText(pair.getFirst());
            indicator.checkCanceled();
          }
          pair.getSecond().run();
        }
      }
    }, "Please wait for safe shutdown of periodical tasks...", true, project);
  }

  public static<T> T safeGetComponent(@NotNull final Project project, final Class<T> componentClass) throws ProcessCanceledException {
    final T component;
    try {
      component = project.getComponent(componentClass);
    }
    catch (Throwable t) {
      if (t instanceof NullPointerException) {
      } else if (t instanceof AssertionError) {
      } else {
        LOG.info(t);
      }
      throw new ProcessCanceledException();
    }
    synchronized (ourLock) {
      final Boolean state = myStates.get(project);
      // if project is already closed and project key is already removed from map - then it should have thrown an exception in the block above
      // so ok to just check for 'closing' stage here
      if (state != null && ! Boolean.TRUE.equals(state)) {
        throw new ProcessCanceledException();
      }
      return component;
    }
  }

  public static void invokeAndWaitInterruptedWhenClosing(final Project project, final Runnable runnable, final ModalityState modalityState) {
    final Ref<Boolean> start = new Ref<Boolean>(Boolean.TRUE);
    final Application application = ApplicationManager.getApplication();
    LOG.assertTrue(! application.isDispatchThread());

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
        synchronized (start) {
          return ! start.get();
        }
      }
    });

    while (true) {
      if (semaphore.waitFor(1000)) {
        return;
      }
      final Ref<Boolean> fire = new Ref<Boolean>();
      if (project != null) {
        synchronized (ourLock) {
          final Boolean state = myStates.get(project);
          if (! Boolean.TRUE.equals(state)) {
            fire.set(Boolean.TRUE);
          }
          if (Boolean.TRUE.equals(fire.get())) {
            synchronized (start) {
              start.set(Boolean.FALSE);
              return;
            }
          }
        }
      }
    }
  }
}
