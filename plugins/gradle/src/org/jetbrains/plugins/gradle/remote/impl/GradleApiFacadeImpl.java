package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.execution.rmi.RemoteServer;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.remote.GradleApiFacade;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:51 PM
 */
public class GradleApiFacadeImpl extends RemoteServer implements GradleApiFacade {

  private final ConcurrentMap<Class<?>, RemoteGradleService> myRemotes  = new ConcurrentHashMap<Class<?>, RemoteGradleService>();
  private final AtomicReference<RemoteGradleProcessSettings> mySettings = new AtomicReference<RemoteGradleProcessSettings>();

  public static void main(String[] args) throws Exception {
    start(new GradleApiFacadeImpl());
  }
  
  @NotNull
  @Override
  public GradleProjectResolver getResolver() throws RemoteException, IllegalStateException {
    try {
      return getRemote(GradleProjectResolverImpl.class);
    }
    catch (Exception e) {
      throw new IllegalStateException(String.format("Can't create '%s' service", GradleProjectResolver.class.getName()), e);
    }
  }

  /**
   * Generic method to retrieve exposed implementations of the target interface.
   * <p/>
   * Uses cached value if it's found; creates new and caches it otherwise.
   * 
   * @param clazz  target service implementation class
   * @param <T>    service implementation class
   * @return       implementation of the target service
   * @throws IllegalAccessException   in case of incorrect assumptions about server class interface
   * @throws InstantiationException   in case of incorrect assumptions about server class interface
   * @throws ClassNotFoundException   in case of incorrect assumptions about server class interface
   */
  @SuppressWarnings("unchecked")
  private <T extends Remote & RemoteGradleService> T getRemote(@NotNull Class<T> clazz)
    throws ClassNotFoundException, IllegalAccessException, InstantiationException
  {
    Object cachedResult = myRemotes.get(clazz);
    if (cachedResult != null) {
      return (T)cachedResult;
    }
    T result = clazz.newInstance();
    RemoteGradleProcessSettings settings = mySettings.get();
    if (settings != null) {
      result.setSettings(settings);
    } 
    try {
      UnicastRemoteObject.exportObject(result, 0);
      T stored = (T)myRemotes.putIfAbsent(clazz, result);
      return stored == null ? result : stored;
    }
    catch (RemoteException e) {
      Object raceResult = myRemotes.get(clazz);
      if (raceResult != null) {
        // Race condition occurred
        return (T)raceResult;
      }
      else {
        throw new IllegalStateException(String.format("Can't prepare remote service for interface '%s'", clazz), e);
      }
    }
  }

  @Override
  public void applySettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    mySettings.set(settings); 
  }
}
