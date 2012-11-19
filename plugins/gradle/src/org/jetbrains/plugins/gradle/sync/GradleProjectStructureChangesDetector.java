package org.jetbrains.plugins.gradle.sync;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.manage.GradleProjectEntityChangeListener;
import org.jetbrains.plugins.gradle.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.task.GradleTaskType;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects project structure changes and triggers linked gradle project update.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:57 PM
 */
public class GradleProjectStructureChangesDetector extends AbstractProjectComponent {
  
  private static final int REFRESH_DELAY_MILLIS = (int)500;

  private final Alarm          myAlarm            = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicLong     myStartRefreshTime = new AtomicLong();
  private final RefreshRequest myRequest          = new RefreshRequest();
  private final AtomicInteger  myImportCounter    = new AtomicInteger();
  
  public GradleProjectStructureChangesDetector(@NotNull Project project) {
    super(project);
    subscribeToGradleImport(project);
    subscribeToRootChanges(project);
  }
  
  private void subscribeToGradleImport(@NotNull Project project) {
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleProjectEntityChangeListener.TOPIC, new GradleProjectEntityChangeListener() {
      @Override
      public void onChangeStart(@NotNull Object entity) {
        myImportCounter.incrementAndGet();
      }

      @Override
      public void onChangeEnd(@NotNull Object entity) {
        if (myImportCounter.decrementAndGet() <= 0) {
          // There is a possible case that we need to add/remove IJ-specific new nodes because of the IJ project structure changes
          // triggered by gradle.
          rebuildTreeModel();
        }
      }
    });    
  }
  
  private void subscribeToRootChanges(@NotNull Project project) {
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        if (myImportCounter.get() <= 0) {
          scheduleUpdate();
        }
      }
    });
  }

  private void rebuildTreeModel() {
    final GradleProjectStructureTreeModel treeModel = GradleUtil.getProjectStructureTreeModel(myProject);
    if (treeModel != null) {
      treeModel.rebuild();
    }
  }
  
  private void scheduleUpdate() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    myStartRefreshTime.set(System.currentTimeMillis() + REFRESH_DELAY_MILLIS);
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myRequest, REFRESH_DELAY_MILLIS);
  }
  
  private class RefreshRequest implements Runnable {
    @Override
    public void run() {
      if (myProject.isDisposed()) {
        myAlarm.cancelAllRequests();
        return;
      }
      if (!myProject.isInitialized()) {
        return;
      }
      myAlarm.cancelAllRequests();
      final GradleTaskManager taskManager = ServiceManager.getService(GradleTaskManager.class);
      if (taskManager != null && taskManager.hasTaskOfTypeInProgress(GradleTaskType.RESOLVE_PROJECT)) {
        return;
      }

      final long diff = System.currentTimeMillis() - myStartRefreshTime.get();
      if (diff < 0) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this, (int)-diff);
        return;
      }

      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (ModalityState.current() != ModalityState.NON_MODAL) {
            // There is a possible case that user performs intellij project structure modification and 'project settings' dialog
            // is open. We want to perform the refresh when the editing is completely finished then.
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(RefreshRequest.this, REFRESH_DELAY_MILLIS);
            return;
          }

          // There is a possible case that we need to add/remove IJ-specific new nodes because of the IJ project structure changes.
          rebuildTreeModel();
          GradleUtil.refreshProject(myProject);
        }
      });
    }
  }
}
