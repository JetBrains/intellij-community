package org.jetbrains.plugins.gradle.sync;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects project structure changes and triggers linked gradle project update.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:57 PM
 */
public class GradleProjectStructureChangesDetector extends AbstractProjectComponent {
  
  private static final int REFRESH_DELAY_MILLIS = (int)TimeUnit.SECONDS.toMillis(2);
  
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicLong myStartRefreshTime = new AtomicLong();
  private final RefreshRequest myRequest = new RefreshRequest();
  
  public GradleProjectStructureChangesDetector(@NotNull Project project) {
    super(project);
    subscribeToRootChanges(project);
  }
  
  private void subscribeToRootChanges(@NotNull Project project) {
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      @Override
      public void rootsChanged(ModuleRootEvent event) {
        scheduleUpdate(); 
      }
    });
  }

  private void scheduleUpdate() {
    myStartRefreshTime.set(System.currentTimeMillis() + REFRESH_DELAY_MILLIS);
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myRequest, REFRESH_DELAY_MILLIS + 16);
  }
  
  private class RefreshRequest implements Runnable {
    @Override
    public void run() {
      if (!myProject.isInitialized()) {
        return;
      }
      myAlarm.cancelAllRequests();
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

          GradleUtil.refreshProject(myProject); 
        }
      });
    }
  }
}
