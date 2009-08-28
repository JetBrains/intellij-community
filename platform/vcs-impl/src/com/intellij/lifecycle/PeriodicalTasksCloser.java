package com.intellij.lifecycle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Pair;
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

  private PeriodicalTasksCloser(final Project project, final ProjectManager projectManager) {
    myInterrupters = new ArrayList<Pair<String, Runnable>>();
    projectManager.addProjectManagerListener(project, this);
  }

  public static PeriodicalTasksCloser getInstance(final Project project) {
    return ServiceManager.getService(project, PeriodicalTasksCloser.class);
  }

  public void register(final String name, final Runnable runnable) {
    myInterrupters.add(new Pair<String, Runnable>(name, runnable));
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
        for (Pair<String, Runnable> pair : myInterrupters) {
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
}
