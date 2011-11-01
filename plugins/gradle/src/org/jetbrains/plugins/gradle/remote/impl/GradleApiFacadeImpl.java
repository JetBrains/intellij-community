package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.execution.rmi.RemoteServer;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.remote.GradleApiFacade;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
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
  
  private final ConcurrentMap<Class<?>, Remote> myRemotes  = new ConcurrentHashMap<Class<?>, Remote>();
  private final AtomicReference<RemoteGradleProcessSettings> mySettings = new AtomicReference<RemoteGradleProcessSettings>();
  private final Alarm myShutdownAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicLong myTtlMs = new AtomicLong(DEFAULT_REMOTE_GRADLE_PROCESS_TTL_IN_MS);
  private final AtomicInteger myCallsInProgressNumber = new AtomicInteger();

  public GradleApiFacadeImpl() {
    updateAutoShutdownTime();
  }

  public static void main(String[] args) throws Exception {
    start(new GradleApiFacadeImpl());
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
  private <I extends Remote, C extends Remote & RemoteGradleService> I getRemote(@NotNull Class<I> interfaceClass,
                                                                                 @NotNull Class<C> implClass) 
    throws ClassNotFoundException, IllegalAccessException, InstantiationException
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
      return stored == null ? (I)result : stored;
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
  public void applySettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    mySettings.set(settings);
    long ttl = settings.getTtlInMs();
    if (ttl > 0) {
      myTtlMs.set(ttl);
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
}
