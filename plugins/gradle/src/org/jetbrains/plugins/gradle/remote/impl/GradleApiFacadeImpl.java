package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.execution.rmi.RemoteServer;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationEvent;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProgressNotificationManager;
import org.jetbrains.plugins.gradle.remote.*;
import org.jetbrains.plugins.gradle.task.GradleTaskType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:51 PM
 */
public class GradleApiFacadeImpl extends RemoteServer implements GradleApiFacade {

  private static final long DEFAULT_REMOTE_GRADLE_PROCESS_TTL_IN_MS = TimeUnit.MILLISECONDS.convert(3, TimeUnit.MINUTES);

  private final ConcurrentMap<Class<?>, RemoteGradleService>       myRemotes
    = new ConcurrentHashMap<Class<?>, RemoteGradleService>();
  private final AtomicReference<RemoteGradleProcessSettings>       mySettings
    = new AtomicReference<RemoteGradleProcessSettings>();
  private final AtomicReference<GradleTaskNotificationListener>    myNotificationListener
    = new AtomicReference<GradleTaskNotificationListener>();
  private final AtomicLong                                         myTtlMs
    = new AtomicLong(DEFAULT_REMOTE_GRADLE_PROCESS_TTL_IN_MS);

  private final Alarm         myShutdownAlarm         = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicInteger myCallsInProgressNumber = new AtomicInteger();
  

  public GradleApiFacadeImpl() {
    updateAutoShutdownTime();
  }

  public static void main(String[] args) throws Exception {
    GradleApiFacadeImpl facade = new GradleApiFacadeImpl();
    facade.init();
    start(facade);
  }

  private void init() throws RemoteException {
    applyProgressManager(RemoteGradleProgressNotificationManager.NULL_OBJECT);
  }
  
  @NotNull
  @Override
  public GradleProjectResolver getResolver() throws RemoteException, IllegalStateException {
    try {
      return getRemote(GradleProjectResolver.class, GradleProjectResolverImpl.class);
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
   * @param interfaceClass  target service interface class
   * @param implClass       target service implementation class
   * @param <I>             service interface class
   * @param <C>             service implementation class
   * @return                implementation of the target service
   * @throws IllegalAccessException   in case of incorrect assumptions about server class interface
   * @throws InstantiationException   in case of incorrect assumptions about server class interface
   * @throws ClassNotFoundException   in case of incorrect assumptions about server class interface
   */
  @SuppressWarnings("unchecked")
  private <I extends RemoteGradleService, C extends I> I getRemote(@NotNull Class<I> interfaceClass, @NotNull Class<C> implClass)
    throws ClassNotFoundException, IllegalAccessException, InstantiationException, RemoteException
  {
    Object cachedResult = myRemotes.get(implClass);
    if (cachedResult != null) {
      return (I)cachedResult;
    }
    final C result = implClass.newInstance();
    RemoteGradleProcessSettings settings = mySettings.get();
    if (settings != null) {
      result.setSettings(settings);
    }
    result.setNotificationListener(myNotificationListener.get());
    I proxy = (I)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { interfaceClass }, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        myCallsInProgressNumber.incrementAndGet();
        try {
          return method.invoke(result, args);
        }
        finally {
          myCallsInProgressNumber.decrementAndGet();
          updateAutoShutdownTime();
        }
      }
    });
    try {
      I stub = (I)UnicastRemoteObject.exportObject(proxy, 0);
      I stored = (I)myRemotes.putIfAbsent(implClass, stub);
      return stored == null ? stub : stored;
    }
    catch (RemoteException e) {
      Object raceResult = myRemotes.get(implClass);
      if (raceResult != null) {
        // Race condition occurred
        return (I)raceResult;
      }
      else {
        throw new IllegalStateException(
          String.format("Can't prepare remote service for interface '%s', implementation '%s'", interfaceClass, implClass),
          e
        );
      }
    }
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    for (RemoteGradleService service : myRemotes.values()) {
      if (service.isTaskInProgress(id)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    Map<GradleTaskType, Set<GradleTaskId>> result = null;
    for (RemoteGradleService service : myRemotes.values()) {
      final Map<GradleTaskType, Set<GradleTaskId>> tasks = service.getTasksInProgress();
      if (tasks.isEmpty()) {
        continue;
      }
      if (result == null) {
        result = new HashMap<GradleTaskType, Set<GradleTaskId>>();
      }
      for (Map.Entry<GradleTaskType, Set<GradleTaskId>> entry : tasks.entrySet()) {
        Set<GradleTaskId> ids = result.get(entry.getKey());
        if (ids == null) {
          result.put(entry.getKey(), ids = new HashSet<GradleTaskId>());
        }
        ids.addAll(entry.getValue());
      }
    }
    if (result == null) {
      result = Collections.emptyMap();
    }
    return result;
  }

  @Override
  public void applySettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    mySettings.set(settings);
    long ttl = settings.getTtlInMs();
    if (ttl > 0) {
      myTtlMs.set(ttl);
    }
    List<RemoteGradleService> services = new ArrayList<RemoteGradleService>(myRemotes.values());
    for (RemoteGradleService service : services) {
      service.setSettings(settings);
    }
  }

  @Override
  public void applyProgressManager(@NotNull RemoteGradleProgressNotificationManager progressManager) throws RemoteException {
    GradleTaskNotificationListener listener = new SwallowingNotificationListener(progressManager);
    myNotificationListener.set(listener);
    List<RemoteGradleService> services = new ArrayList<RemoteGradleService>(myRemotes.values());
    for (RemoteGradleService service : services) {
      service.setNotificationListener(listener);
    }
  }
  
  /**
   * Schedules automatic process termination in {@code #REMOTE_GRADLE_PROCESS_TTL_IN_MS} milliseconds.
   * <p/>
   * Rationale: it's possible that IJ user performs gradle related activity (e.g. import from gradle) when the works purely
   * at IJ. We don't want to keep remote process that communicates with the gradle api then.
   */
  private void updateAutoShutdownTime() {
    myShutdownAlarm.cancelAllRequests();
    myShutdownAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myCallsInProgressNumber.get() > 0) {
          updateAutoShutdownTime();
          return;
        }
        System.exit(0);
      }
    }, (int)myTtlMs.get());
  }
  
  private static class SwallowingNotificationListener implements GradleTaskNotificationListener {
    
    private final RemoteGradleProgressNotificationManager myManager;

    SwallowingNotificationListener(@NotNull RemoteGradleProgressNotificationManager manager) {
      myManager = manager;
    }

    @Override
    public void onQueued(@NotNull GradleTaskId id) {
    }

    @Override
    public void onStart(@NotNull GradleTaskId id) {
      try {
        myManager.onStart(id);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public void onStatusChange(@NotNull GradleTaskNotificationEvent event) {
      try {
        myManager.onStatusChange(event);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public void onEnd(@NotNull GradleTaskId id) {
      try {
        myManager.onEnd(id);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }
  }
}
