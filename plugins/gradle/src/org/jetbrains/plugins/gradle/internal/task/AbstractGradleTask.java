package org.jetbrains.plugins.gradle.internal.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManager;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationEvent;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListenerAdapter;
import org.jetbrains.plugins.gradle.remote.GradleApiFacade;
import org.jetbrains.plugins.gradle.remote.GradleApiFacadeManager;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates particular task performed by the gradle integration.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 7:03 AM
 */
public abstract class AbstractGradleTask implements GradleTask {

  private static final Logger LOG = Logger.getInstance("#" + AbstractGradleTask.class.getName());

  private final AtomicReference<GradleTaskState> myState = new AtomicReference<GradleTaskState>(GradleTaskState.NOT_STARTED);
  private final AtomicReference<Throwable>       myError = new AtomicReference<Throwable>();

  @Nullable transient private final Project      myIdeProject;
  @NotNull private final            GradleTaskId myId;

  protected AbstractGradleTask(@Nullable Project project, @NotNull GradleTaskType type) {
    myIdeProject = project;
    myId = GradleTaskId.create(type);
  }

  @NotNull
  public GradleTaskId getId() {
    return myId;
  }

  @NotNull
  public GradleTaskState getState() {
    return myState.get();
  }

  protected void setState(@NotNull GradleTaskState state) {
    myState.set(state);
  }

  @Override
  public Throwable getError() {
    return myError.get();
  }

  @Nullable
  public Project getIdeProject() {
    return myIdeProject;
  }

  public void refreshState() {
    if (getState() != GradleTaskState.IN_PROGRESS) {
      return;
    }
    final GradleApiFacadeManager manager = ServiceManager.getService(GradleApiFacadeManager.class);
    try {
      final GradleApiFacade facade = manager.getFacade(myIdeProject);
      setState(facade.isTaskInProgress(getId()) ? GradleTaskState.IN_PROGRESS : GradleTaskState.FAILED);
    }
    catch (Throwable e) {
      setState(GradleTaskState.FAILED);
      myError.set(e);
      if (myIdeProject == null || !myIdeProject.isDisposed()) {
        LOG.warn(e);
      }
    }
  }
  
  @Override
  public void execute(@NotNull final ProgressIndicator indicator, @NotNull GradleTaskNotificationListener ... listeners) {
    indicator.setIndeterminate(true);
    GradleTaskNotificationListenerAdapter adapter = new GradleTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull GradleTaskNotificationEvent event) {
        indicator.setText(wrapProgressText(event.getDescription()));
      }
    };
    final GradleTaskNotificationListener[] ls;
    if (listeners.length > 0) {
      ls = ArrayUtil.append(listeners, adapter);
    }
    else {
      ls = new GradleTaskNotificationListener[] { adapter };
    }
    
    execute(ls);
  }
  
  @Override
  public void execute(@NotNull GradleTaskNotificationListener... listeners) {
    GradleProgressNotificationManager progressManager = ServiceManager.getService(GradleProgressNotificationManager.class);
    for (GradleTaskNotificationListener listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }
    try {
      doExecute();
    }
    catch (Throwable e) {
      setState(GradleTaskState.FAILED);
      myError.set(e);
      LOG.warn(e);
    }
    finally {
      for (GradleTaskNotificationListener listener : listeners) {
        progressManager.removeNotificationListener(listener);
      }
    }
  }

  protected abstract void doExecute() throws Exception;

  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    return GradleBundle.message("gradle.general.progress.update.text", text);
  }
  
  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractGradleTask task = (AbstractGradleTask)o;
    return myId.equals(task.myId);
  }

  @Override
  public String toString() {
    return String.format("%s: %s", myId, myState);
  }
}
