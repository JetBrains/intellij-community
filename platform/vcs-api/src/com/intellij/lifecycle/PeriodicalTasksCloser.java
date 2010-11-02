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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.Rethrow;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class PeriodicalTasksCloser implements ProjectManagerListener, ProjectLifecycleListener, ApplicationComponent {
  private final static Logger LOG = Logger.getInstance("#com.intellij.lifecycle.PeriodicalTasksCloser");
  private final Object myLock = new Object();
  private final MultiMap<Project, Pair<String, Runnable>> myInterrupters;
  //private final Map<Project, TracedLifeCycle> myStates = new HashMap<Project, TracedLifeCycle>();
  private MessageBusConnection myConnection;
  private ProjectManager myProjectManager;

  private PeriodicalTasksCloser(final ProjectManager projectManager) {
    myInterrupters = new MultiMap<Project, Pair<String, Runnable>>();
    myProjectManager = projectManager;
    myProjectManager.addProjectManagerListener(this);
    myConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myConnection.subscribe(ProjectLifecycleListener.TOPIC, this);
  }

  public static PeriodicalTasksCloser getInstance() {
    return ApplicationManager.getApplication().getComponent(PeriodicalTasksCloser.class);
  }

  @Override
  public void disposeComponent() {
    /*synchronized (myLock) {
      myStates.clear(); // +-
    }*/
    myProjectManager.removeProjectManagerListener(this);
    myConnection.disconnect();
    synchronized (myLock) {
      myInterrupters.clear();
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return PeriodicalTasksCloser.class.getName();
  }

  @Override
  public void initComponent() {
  }

  public boolean register(final Project project, final String name, final Runnable runnable) {
    synchronized (myLock) {
      if (project.isDisposed()) {
        return false;
      }
      /*if (Boolean.FALSE.equals(myStates.get(project))) {
        return false;
      }*/
      myInterrupters.putValue(project, new Pair<String, Runnable>(name, runnable));
      return true;
    }
  }

  public void projectOpened(Project project) {
    clearForTests();
    /*clearForTests();

    synchronized (myLock) {
      myStates.put(project, TracedLifeCycle.OPEN);
    }*/
  }

  public boolean canCloseProject(Project project) {
    return true;
  }

  private void clearForTests() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final Project[] projects = myProjectManager.getOpenProjects();
      synchronized (myLock) {
        myInterrupters.keySet().retainAll(Arrays.asList(projects));
      }
    }
  }

  public void projectClosed(Project project) {
    /*synchronized (myLock) {
      myStates.remove(project);
    }*/
  }

  public void projectClosing(final Project project) {
    /*synchronized (myLock) {
      myStates.put(project, TracedLifeCycle.CLOSING);
    }*/
    final Collection<Pair<String, Runnable>> list;
    synchronized (myLock) {
      list = myInterrupters.remove(project);
    }
    if (list != null) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
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
  }

  @Override
  public void afterProjectClosed(@NotNull Project project) {
  }

  @Override
  public void projectComponentsInitialized(Project project) {
  }

  @Override
  public void beforeProjectLoaded(@NotNull Project project) {
    clearForTests();
    /*clearForTests();

    synchronized (myLock) {
      myStates.put(project, TracedLifeCycle.OPENING);
    }*/
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

  private void throwCanceledException(final Project project, final Throwable t) {
    synchronized (myLock) {
      // allow NPE & assertion _catch_ only if project is closed and being disposed
      if (project.isOpen()) {
        Rethrow.reThrowRuntime(t);
      }
    }
    throw new ProcessCanceledException();
  }

  public void invokeAndWaitInterruptedWhenClosing(final Project project, final Runnable runnable, final ModalityState modalityState) {
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
        synchronized (myLock) {
          if (! project.isOpen()) {
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
