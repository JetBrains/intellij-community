package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.task.GradleTaskAware;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.task.GradleTaskType;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Serves as a facade for working with
 * <a href="http://gradle.org/current/docs/javadoc/org/gradle/tooling/package-summary.html">gradle tooling api</a>.
 * <p/>
 * The main idea is that we don't want to use it directly from IntelliJ IDEA process (to avoid unnecessary heap/perm gen pollution, 
 * memory leaks etc). So, we use it at external process and current class works as a facade to it from IntelliJ process.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 10:52 AM
 */
public interface GradleApiFacade extends Remote, GradleTaskAware {

  /** <a href="http://en.wikipedia.org/wiki/Null_Object_pattern">Null object</a> for {@link GradleApiFacade}. */
  GradleApiFacade NULL_OBJECT = new GradleApiFacade() {
    @NotNull
    @Override
    public GradleProjectResolver getResolver() throws RemoteException, IllegalStateException {
      return GradleProjectResolver.NULL_OBJECT;
    }

    @Override
    public void applySettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    }

    @Override
    public void applyProgressManager(@NotNull RemoteGradleProgressNotificationManager progressManager) throws RemoteException {
    }

    @Override
    public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
      return false;
    }

    @NotNull
    @Override
    public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
      return Collections.emptyMap();
    }
  };
  
  /**
   * Exposes <code>'resolve gradle project'</code> service that works at another process.
   * 
   * @return                        <code>'resolve gradle project'</code> service
   * @throws RemoteException        in case of unexpected I/O exception during processing
   * @throws IllegalStateException  in case of inability to create the service
   */
  @NotNull
  GradleProjectResolver getResolver() throws RemoteException, IllegalStateException;

  /**
   * Asks remote gradle process to apply given settings.
   * 
   * @param settings            settings to apply
   * @throws RemoteException    in case of unexpected I/O exception during processing
   */
  void applySettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException;

  /**
   * Asks remote gradle process to use given progress manager.
   * 
   * @param progressManager  progress manager to use
   * @throws RemoteException    in case of unexpected I/O exception during processing
   */
  void applyProgressManager(@NotNull RemoteGradleProgressNotificationManager progressManager) throws RemoteException;
}
