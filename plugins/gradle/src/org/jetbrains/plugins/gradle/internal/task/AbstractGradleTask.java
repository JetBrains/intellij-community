package org.jetbrains.plugins.gradle.internal.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;

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

  @Nullable transient private final Project              myIdeProject;
  @NotNull private final            ExternalSystemTaskId myId;

  protected AbstractGradleTask(@Nullable Project project, @NotNull ExternalSystemTaskType type) {
    myIdeProject = project;
    myId = ExternalSystemTaskId.create(type);
  }

  @NotNull
  public ExternalSystemTaskId getId() {
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
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    try {
      // TODO den implement
//      final RemoteExternalSystemFacade facade = manager.getFacade(myIdeProject);
//      setState(facade.isTaskInProgress(getId()) ? GradleTaskState.IN_PROGRESS : GradleTaskState.FAILED);
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
  public void execute(@NotNull final ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners) {
    indicator.setIndeterminate(true);
    ExternalSystemTaskNotificationListenerAdapter adapter = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        indicator.setText(wrapProgressText(event.getDescription()));
      }
    };
    final ExternalSystemTaskNotificationListener[] ls;
    if (listeners.length > 0) {
      ls = ArrayUtil.append(listeners, adapter);
    }
    else {
      ls = new ExternalSystemTaskNotificationListener[] { adapter };
    }
    
    execute(ls);
  }
  
  @Override
  public void execute(@NotNull ExternalSystemTaskNotificationListener... listeners) {
    ExternalSystemProgressNotificationManager progressManager = ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    for (ExternalSystemTaskNotificationListener listener : listeners) {
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
      for (ExternalSystemTaskNotificationListener listener : listeners) {
        progressManager.removeNotificationListener(listener);
      }
    }
  }

  protected abstract void doExecute() throws Exception;

  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    // TODO den implement
    return "";
//    return ExternalSystemBundle.message("gradle.general.progress.update.text", text);
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
