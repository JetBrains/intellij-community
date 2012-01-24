package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.task.GradleTaskType;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;

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
public interface GradleApiFacade extends Remote {

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

  /**
   * Asks remote gradle process to check if a task with the given id is being executed right now.
   * 
   * @param id  target task's id
   * @return    <code>true</code> if a task with the given id is executed at the moment; <code>false</code> otherwise
   * @throws RemoteException    in case of unexpected I/O exception during processing
   */
  boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException;

  /**
   * Allows to ask remote gradle process for the ids of the tasks with the given type being executed now.  
   * 
   * @param type  target task type
   * @return      ids of the tasks of the target type being executed at the moment (if any)
   * @throws RemoteException    in case of unexpected I/O exception during processing
   */
  @NotNull
  Collection<GradleTaskId> getTasksInProgress(@NotNull GradleTaskType type) throws RemoteException;
}
