// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class EventDispatcher<T extends EventListener> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EventDispatcher");

  private final T myMulticaster;

  private final List<T> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public static <T extends EventListener> EventDispatcher<T> create(@NotNull Class<T> listenerClass) {
    return new EventDispatcher<T>(listenerClass, null);
  }

  @NotNull
  public static <T extends EventListener> EventDispatcher<T> create(@NotNull Class<T> listenerClass, @NotNull Map<String, Object> methodReturnValues) {
    assertNonVoidMethodReturnValuesAreDeclared(methodReturnValues, listenerClass);
    return new EventDispatcher<T>(listenerClass, methodReturnValues);
  }

  private static void assertNonVoidMethodReturnValuesAreDeclared(@NotNull Map<String, Object> methodReturnValues,
                                                                 @NotNull Class<?> listenerClass) {
    List<Method> declared = new ArrayList<Method>(ReflectionUtil.getClassPublicMethods(listenerClass));
    for (final Map.Entry<String, Object> entry : methodReturnValues.entrySet()) {
      final String methodName = entry.getKey();
      Method found = ContainerUtil.find(declared, new Condition<Method>() {
        @Override
        public boolean value(Method m) {
          return methodName.equals(m.getName());
        }
      });
      assert found != null : "Method " + methodName + " must be declared in " + listenerClass;
      assert !found.getReturnType().equals(void.class) :
        "Method " + methodName + " must be non-void if you want to specify what its proxy should return";
      Object returnValue = entry.getValue();
      assert ReflectionUtil.boxType(found.getReturnType()).isAssignableFrom(returnValue.getClass()) :
        "You specified that method " +
        methodName + " proxy will return " + returnValue +
        " but its return type is " + found.getReturnType() + " which is incompatible with " + returnValue.getClass();
      declared.remove(found);
    }
    for (Method method : declared) {
      assert method.getReturnType().equals(void.class) :
        "Method "+method+" returns "+method.getReturnType()+" and yet you didn't specify what its proxy should return";
    }
  }

  private EventDispatcher(@NotNull Class<T> listenerClass, @Nullable Map<String, Object> methodReturnValues) {
    myMulticaster = createMulticaster(listenerClass, methodReturnValues, new Getter<Iterable<T>>() {
      @Override
      public Iterable<T> get() {
        return myListeners;
      }
    });
  }

  @NotNull
  static <T> T createMulticaster(@NotNull Class<T> listenerClass,
                                 @Nullable final Map<String, Object> methodReturnValues,
                                 final Getter<? extends Iterable<T>> listeners) {
    LOG.assertTrue(listenerClass.isInterface(), "listenerClass must be an interface");
    InvocationHandler handler = new InvocationHandler() {
      @Override
      @NonNls
      public Object invoke(Object proxy, final Method method, final Object[] args) {
        @NonNls String methodName = method.getName();
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          return handleObjectMethod(proxy, args, methodName);
        }
        else if (methodReturnValues != null && methodReturnValues.containsKey(methodName)) {
          return methodReturnValues.get(methodName);
        }
        else {
          dispatchVoidMethod(listeners.get(), method, args);
          return null;
        }
      }
    };

    //noinspection unchecked
    return (T)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, handler);
  }

  @Nullable
  public static Object handleObjectMethod(Object proxy, Object[] args, String methodName) {
    if (methodName.equals("toString")) {
      return "Multicaster";
    }
    else if (methodName.equals("hashCode")) {
      return System.identityHashCode(proxy);
    }
    else if (methodName.equals("equals")) {
      return proxy == args[0] ? Boolean.TRUE : Boolean.FALSE;
    }
    else {
      LOG.error("Incorrect Object's method invoked for proxy:" + methodName);
      return null;
    }
  }

  @NotNull
  public T getMulticaster() {
    return myMulticaster;
  }

  private static <T> void dispatchVoidMethod(@NotNull Iterable<T> listeners, @NotNull Method method, Object[] args) {
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
