// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PendingEventDispatcher <T extends EventListener> {
  private static final Logger LOG = Logger.getInstance(PendingEventDispatcher.class);

  private final T myMulticaster;

  private final List<T> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Map<T, Boolean> myListenersState = new HashMap<>();

  private final Stack<T> myDispatchingListeners = new Stack<>();

  private Method myCurrentDispatchMethod = null;
  private Object[] myCurrentDispatchArgs = null;

  private static int ourDispatchingEventsCounter = 0;
  private final boolean myAssertDispatchThread;

  public static <T extends EventListener> PendingEventDispatcher<T> create(Class<T> listenerClass) {
    return create(listenerClass, true);
  }

  public static <T extends EventListener> PendingEventDispatcher<T> create(Class<T> listenerClass, boolean assertDispatchThread) {
    return new PendingEventDispatcher<>(listenerClass, assertDispatchThread);
  }

  public static boolean isDispatchingAnyEvent(){
    return ourDispatchingEventsCounter > 0;
  }

  public boolean isDispatching(){
    return myCurrentDispatchMethod != null;
  }

  private PendingEventDispatcher(Class<T> listenerClass, boolean assertDispatchThread) {
    myAssertDispatchThread = assertDispatchThread;
    InvocationHandler handler = new InvocationHandler() {
      @Override
      @NonNls public Object invoke(Object proxy, final Method method, final Object[] args) {
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          @NonNls String methodName = method.getName();
          return switch (methodName) {
            case "toString" -> "Multicaster";
            case "hashCode" -> Integer.valueOf(System.identityHashCode(proxy));
            case "equals" -> proxy == args[0] ? Boolean.TRUE : Boolean.FALSE;
            default -> {
              LOG.error("Incorrect Object's method invoked for proxy:" + methodName);
              yield null;
            }
          };
        }
        else {
          dispatch(method, args);
          return null;
        }
      }
    };

    myMulticaster = ReflectionUtil.proxy(listenerClass, handler);
  }

  public boolean hasListeners() {
    return !myListeners.isEmpty();
  }

  public T getMulticaster() {
    return myMulticaster;
  }

  public synchronized void addListener(T listener) {
    //LOG.assertTrue(!myListeners.containsKey(listener), "Cannot add the same listener twice");

    myListeners.add(listener);
    myListenersState.put(listener, Boolean.TRUE);
  }

  public synchronized void addListener(final T listener, Disposable parentDisposable) {
    addListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeListener(listener);
      }
    });
  }

  public synchronized void removeListener(T listener) {
    //LOG.assertTrue(myListeners.containsKey(listener), "Attempt to remove a non-registered listener");

    myListeners.remove(listener);
    myListenersState.remove(listener);
  }

  public void dispatchPendingEvent(final T listener) {
    Boolean dispatched = myListenersState.get(listener);
    //if (!LOG.assertTrue(dispatched != null, "dispatchPendingEvents() should not be invoked for listener which was not registered")) return;
    if (dispatched == null) return;

    if (!dispatched.booleanValue()) {
      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        invoke(listener);
      }
      else {
        // We do not dispatch events in non swing thread. A potential deadlock otherwise.
        return;
        /*
        ModalityState state = ProgressManager.getInstance().getProgressIndicator().getModalityState();
        application.invokeAndWait(new Runnable() {
          public void run() {
            invoke(listener);
          }
        }, state);
        */
      }
    }

    /* Seems like this assertion is incorrect and there's nothing wrong with just skipping in this situation.
    else {
      if (myDispatchingListeners.size() > 0 && listener != myDispatchingListeners.peek() && myDispatchingListeners.contains(listener)){
        LOG.error("Cyclic dispatching is prohibited");
      }
    }
    */
  }

  private void assertDispatchThread() {
    Application application = ApplicationManager.getApplication();
    if (myAssertDispatchThread && !application.isUnitTestMode()) {
      application.assertIsDispatchThread();
    }
  }

  private void dispatch(Method method, Object[] args) {
    assertDispatchThread();
    if(myCurrentDispatchMethod != null) {
      LOG.error("Event cannot be raised when dispatching another event is in progress. Dispatching " + myCurrentDispatchMethod.getName());
    }

    method.setAccessible(true);

    ourDispatchingEventsCounter++;
    myCurrentDispatchMethod = method;
    myCurrentDispatchArgs = args;

    try {
      List<T> listeners = getListeners();
      for (T listener : listeners) {
        myListenersState.put(listener, Boolean.FALSE);
      }

      for (T listener : listeners) {
        invoke(listener);
      }
    }
    finally {
      ourDispatchingEventsCounter--;
      myCurrentDispatchMethod = null;
      myCurrentDispatchArgs = null;
    }
  }

  private void invoke(T listener) {
    Boolean state = myListenersState.get(listener);
    if (state == null/*removed*/ || state.booleanValue()) {
      return;
    }
    myListenersState.put(listener, Boolean.TRUE);

    try {
      myDispatchingListeners.push(listener);
      myCurrentDispatchMethod.invoke(listener, myCurrentDispatchArgs);
    }
    catch(AbstractMethodError e) {
      //Do nothing. This listener just does not implement something newly added yet.
    }
    catch (InvocationTargetException | IllegalAccessException e) {
      LOG.error(e.getCause());
    }
    finally {
      myDispatchingListeners.pop();
    }
  }


  public List<T> getListeners() {
    return myListeners;
  }
}
