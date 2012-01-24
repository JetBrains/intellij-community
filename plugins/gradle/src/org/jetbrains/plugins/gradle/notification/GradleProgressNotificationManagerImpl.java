package org.jetbrains.plugins.gradle.notification;

import com.intellij.execution.rmi.RemoteObject;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProgressNotificationManager;
import org.jetbrains.plugins.gradle.task.GradleTaskId;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Denis Zhdanov
 * @since 11/10/11 11:56 AM
 */
public class GradleProgressNotificationManagerImpl extends RemoteObject
  implements GradleProgressNotificationManager, RemoteGradleProgressNotificationManager
{

  private final ConcurrentMap<GradleTaskNotificationListener, Set<GradleTaskId>> myListeners
    = new ConcurrentHashMap<GradleTaskNotificationListener, Set<GradleTaskId>>();

  @Override
  public boolean addNotificationListener(@NotNull GradleTaskId taskId, @NotNull GradleTaskNotificationListener listener) {
    Set<GradleTaskId> ids = null;
    while (ids == null) {
      if (myListeners.containsKey(listener)) {
        ids = myListeners.get(listener);
      }
      else {
        ids = myListeners.putIfAbsent(listener, new ConcurrentHashSet<GradleTaskId>());
      }
    }
    return ids.add(taskId);
  }

  @Override
  public boolean removeNotificationListener(@NotNull GradleTaskNotificationListener listener) {
    return myListeners.remove(listener) != null;
  }

  @Override
  public void onStart(@NotNull GradleTaskId id) {
    for (Map.Entry<GradleTaskNotificationListener, Set<GradleTaskId>> entry : myListeners.entrySet()) {
      if (entry.getValue().contains(id)) {
        entry.getKey().onStart(id);
      }
    } 
  }

  @Override
  public void onStatusChange(@NotNull GradleTaskNotificationEvent event) {
    for (Map.Entry<GradleTaskNotificationListener, Set<GradleTaskId>> entry : myListeners.entrySet()) {
      if (entry.getValue().contains(event.getId())) {
        entry.getKey().onStatusChange(event);
      }
    } 
  }

  @Override
  public void onEnd(@NotNull GradleTaskId id) {
    for (Map.Entry<GradleTaskNotificationListener, Set<GradleTaskId>> entry : myListeners.entrySet()) {
      if (entry.getValue().contains(id)) {
        entry.getKey().onEnd(id);
      }
    } 
  }
}
