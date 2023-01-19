// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;

public final class EventDispatcher<T extends EventListener> {
  private static final Logger LOG = Logger.getInstance(EventDispatcher.class);

  private T myMulticaster;

  private final DisposableWrapperList<T> myListeners = new DisposableWrapperList<>();
  private final @NotNull Class<T> myListenerClass;
  private final @Nullable Map<String, Object> myMethodReturnValues;

  public static @NotNull <T extends EventListener> EventDispatcher<T> create(@NotNull Class<T> listenerClass) {
    return new EventDispatcher<>(listenerClass, null);
  }

  public static @NotNull <T extends EventListener> EventDispatcher<T> create(@NotNull Class<T> listenerClass, @NotNull Map<String, Object> methodReturnValues) {
    assertNonVoidMethodReturnValuesAreDeclared(methodReturnValues, listenerClass);
    return new EventDispatcher<>(listenerClass, methodReturnValues);
  }

  private static void assertNonVoidMethodReturnValuesAreDeclared(@NotNull Map<String, Object> methodReturnValues,
                                                                 @NotNull Class<?> listenerClass) {
    List<Method> declared = new ArrayList<>(ReflectionUtil.getClassPublicMethods(listenerClass));
    for (final Map.Entry<String, Object> entry : methodReturnValues.entrySet()) {
      final String methodName = entry.getKey();
      Method found = ContainerUtil.find(declared, m -> methodName.equals(m.getName()));
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
    myListenerClass = listenerClass;
    myMethodReturnValues = methodReturnValues;
  }

  public static <T> T createMulticaster(@NotNull Class<T> listenerClass,
                                        @NotNull Supplier<? extends Iterable<T>> listeners) {
    return createMulticaster(listenerClass, null, listeners);
  }

  static @NotNull <T> T createMulticaster(@NotNull Class<T> listenerClass,
                                          @Nullable Map<String, Object> methodReturnValues,
                                          @NotNull Supplier<? extends Iterable<T>> listeners) {
    LOG.assertTrue(listenerClass.isInterface(), "listenerClass must be an interface: " + listenerClass.getName());
    //noinspection unchecked
    return (T)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, (proxy, method, args) -> {
      String methodName = method.getName();
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
    });
  }

  public static @Nullable Object handleObjectMethod(Object proxy, Object[] args, String methodName) {
    switch (methodName) {
      case "toString":
        return "Multicaster";
      case "hashCode":
        return System.identityHashCode(proxy);
      case "equals":
        return proxy == args[0] ? Boolean.TRUE : Boolean.FALSE;
      default:
        LOG.error("Incorrect Object's method invoked for proxy:" + methodName);
        return null;
    }
  }

  public @NotNull T getMulticaster() {
    T multicaster = myMulticaster;
    if (multicaster == null) {
      // benign race
      myMulticaster = multicaster = createMulticaster(myListenerClass, myMethodReturnValues, () -> myListeners);
    }
    return multicaster;
  }

  private static <T> void dispatchVoidMethod(@NotNull Iterable<? extends T> listeners, @NotNull Method method, Object[] args) {
    List<Throwable> exceptions = null;
    method.setAccessible(true);

    for (T listener : listeners) {
      try {
        method.invoke(listener, args);
      }
      catch (Throwable e) {
        exceptions = handleException(e, exceptions);
      }
    }

    if (exceptions != null) {
      throwExceptions(exceptions);
    }
  }

  public static @Nullable List<Throwable> handleException(@NotNull Throwable e, @Nullable List<Throwable> exceptions) {
    Throwable exception = e;
    if (e instanceof InvocationTargetException) {
      Throwable cause = e.getCause();
      if (cause != null) {
        // Do nothing for AbstractMethodError. This listener just does not implement something newly added yet.
        if (cause instanceof AbstractMethodError) {
          return exceptions;
        }

        exception = cause;
      }
    }

    if (exceptions == null) {
      exceptions = new ArrayList<>();
    }
    exceptions.add(exception);
    return exceptions;
  }

  private static void throwExceptions(@NotNull List<? extends Throwable> exceptions) {
    if (exceptions.size() == 1) {
      ExceptionUtil.rethrow(exceptions.get(0));
    }
    else {
      for (Throwable exception : exceptions) {
        if (exception instanceof ProcessCanceledException) {
          throw (ProcessCanceledException)exception;
        }
      }
      throw new CompoundRuntimeException(exceptions);
    }
  }

  public void addListener(@NotNull T listener) {
    myListeners.add(listener);
  }

  public void addListener(@NotNull T listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener, parentDisposable);
  }

  public void removeListener(@NotNull T listener) {
    myListeners.remove(listener);
  }

  public boolean hasListeners() {
    return !myListeners.isEmpty();
  }

  public @NotNull List<T> getListeners() {
    return myListeners;
  }

  @TestOnly
  public void neuterMultiCasterWhilePerformanceTestIsRunningUntil(@NotNull Disposable disposable) {
    T multicaster = myMulticaster;
    myMulticaster = createMulticaster(myListenerClass, myMethodReturnValues, () -> Collections.emptyList());
    Disposer.register(disposable, () -> myMulticaster = multicaster);
  }
}
