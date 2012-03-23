package org.jetbrains.plugins.gradle.task;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManager;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationEvent;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.GradleApiFacadeManager;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Provides gradle tasks monitoring and management facilities.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 1:52 PM
 */
public class GradleTaskManager extends AbstractProjectComponent implements GradleTaskNotificationListener {

  /**
   * We receive information about the tasks being enqueued to the slave gradle projects here. However, there is a possible
   * situation when particular task has been sent to execution but remote side has not been responding for a while. There at least
   * two possible explanations then:
   * <pre>
   * <ul>
   *   <li>The task is still in progress (e.g. great number of libraries is being downloaded);</li>
   *   <li>Remote side has fallen (uncaught exception; manual slave gradle process kill etc);</li>
   * </ul>
   * </pre>
   * We need to distinguish between them, so, we perform 'task pings' if any task is executed too long. Current constant holds
   * criteria of 'too long execution'.
   */
  private static final long REFRESH_DELAY_MILLIS                 = TimeUnit.SECONDS.toMillis(10);
  private static final int  DETECT_HANGED_TASKS_FREQUENCY_MILLIS = (int)TimeUnit.SECONDS.toMillis(5);
  
  @NotNull private final ConcurrentMap<GradleTaskId, Long> myTasksInProgress = new ConcurrentHashMap<GradleTaskId, Long>();
  @NotNull private final Alarm                             myAlarm           = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  @NotNull private final GradleApiFacadeManager            myFacadeManager;

  public GradleTaskManager(@NotNull Project project,
                           @NotNull GradleApiFacadeManager facadeManager,
                           @NotNull GradleProgressNotificationManager notificationManager)
  {
    super(project);
    myFacadeManager = facadeManager;
    notificationManager.addNotificationListener(this);
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        try {
          for (Long limit : myTasksInProgress.values()) {
            if (limit <= System.currentTimeMillis()) {
              update();
              break;
            }
          }
        }
        finally {
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(this, DETECT_HANGED_TASKS_FREQUENCY_MILLIS);
        }
      }
    }, DETECT_HANGED_TASKS_FREQUENCY_MILLIS);
  }
  
  /**
   * Allows to check if any task of the given type is being executed at the moment.  
   *
   * @param type  target task type
   * @return      <code>true</code> if any task of the given type is being executed at the moment;
   *              <code>false</code> otherwise
   */
  public boolean hasTaskOfTypeInProgress(@NotNull GradleTaskType type) {
    for (GradleTaskId id : myTasksInProgress.keySet()) {
      if (type.equals(id.getType())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onQueued(@NotNull GradleTaskId id) {
    myTasksInProgress.put(id, System.currentTimeMillis() + REFRESH_DELAY_MILLIS); 
  }

  @Override
  public void onStart(@NotNull GradleTaskId id) {
    myTasksInProgress.put(id, System.currentTimeMillis() + REFRESH_DELAY_MILLIS);
  }

  @Override
  public void onStatusChange(@NotNull GradleTaskNotificationEvent event) {
    myTasksInProgress.put(event.getId(), System.currentTimeMillis() + REFRESH_DELAY_MILLIS); 
  }

  @Override
  public void onEnd(@NotNull GradleTaskId id) {
    myTasksInProgress.remove(id);
  }

  public void update() {
    try {
      final Map<GradleTaskType, Set<GradleTaskId>> currentState = myFacadeManager.getFacade(myProject).getTasksInProgress();
      myTasksInProgress.clear();
      for (Set<GradleTaskId> ids : currentState.values()) {
        for (GradleTaskId id : ids) {
          myTasksInProgress.put(id, System.currentTimeMillis() + REFRESH_DELAY_MILLIS);
        }
      }
    }
    catch (Exception e) {
      GradleLog.LOG.warn(String.format("Can't refresh active tasks. Reason: %s", GradleUtil.buildErrorMessage(e)));
    }
  }
}
