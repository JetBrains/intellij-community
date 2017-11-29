/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EventListener;
import java.util.List;

/**
 * @author max
 */
public class EventDispatcher<T extends EventListener> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EventDispatcher");

  private final T myMulticaster;

  private final List<T> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public static <T extends EventListener> EventDispatcher<T> create(@NotNull Class<T> listenerClass) {
    return new EventDispatcher<T>(listenerClass);
  }

  private EventDispatcher(@NotNull Class<T> listenerClass) {
    myMulticaster = createMulticaster(listenerClass, new Getter<Iterable<T>>() {
      @Override
      public Iterable<T> get() {
        return myListeners;
      }
    });
  }

  @NotNull
  static <T> T createMulticaster(@NotNull Class<T> listenerClass, final Getter<Iterable<T>> listeners) {
    LOG.assertTrue(listenerClass.isInterface(), "listenerClass must be an interface");
    InvocationHandler handler = new InvocationHandler() {
      @Override
      @NonNls
      public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          @NonNls String methodName = method.getName();
          if (methodName.equals("toString")) {
            return "Multicaster";
          }
          else if (methodName.equals("hashCode")) {
            return Integer.valueOf(System.identityHashCode(proxy));
          }
          else if (methodName.equals("equals")) {
            return proxy == args[0] ? Boolean.TRUE : Boolean.FALSE;
          }
          else {
            LOG.error("Incorrect Object's method invoked for proxy:" + methodName);
            return null;
          }
        }
        else {
          dispatch(listeners.get(), method, args);
          return null;
        }
      }
    };

    //noinspection unchecked
    return (T)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, handler);
  }

  @NotNull
  public T getMulticaster() {
    return myMulticaster;
  }

  private static <T> void dispatch(Iterable<T> listeners, @NotNull Method method, Object[] args) {
    method.setAccessible(true);

    for (T listener : listeners) {
      try {
        method.invoke(listener, args);
      }
      catch (AbstractMethodError ignored) {
        // Do nothing. This listener just does not implement something newly added yet.
        // AbstractMethodError is normally wrapped in InvocationTargetException,
        // but some Java versions didn't do it in some cases (see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6531596)
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        final Throwable cause = e.getCause();
        ExceptionUtil.rethrowUnchecked(cause);
        if (!(cause instanceof AbstractMethodError)) { // AbstractMethodError means this listener doesn't implement some new method in interface
          LOG.error(cause);
        }
      }
    }
  }

  public void addListener(@NotNull T listener) {
    myListeners.add(listener);
  }

  /**
   * CAUTION: do not use in pair with {@link #removeListener(EventListener)}: a memory leak can occur.
   * In case a listener is removed, it's disposable stays in disposable hierarchy, preventing the listener from being gc'ed.
   */
  public void addListener(@NotNull final T listener, @NotNull Disposable parentDisposable) {
    addListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeListener(listener);
      }
    });
  }

  public void removeListener(@NotNull T listener) {
    myListeners.remove(listener);
  }

  public boolean hasListeners() {
    return !myListeners.isEmpty();
  }

  @NotNull
  public List<T> getListeners() {
    return myListeners;
  }
}
