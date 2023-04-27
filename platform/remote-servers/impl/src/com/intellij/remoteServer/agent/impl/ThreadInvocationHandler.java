// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.agent.annotation.AsyncCall;
import com.intellij.remoteServer.agent.annotation.ChildCall;
import com.intellij.remoteServer.agent.annotation.FinalCall;
import com.intellij.remoteServer.agent.annotation.ImmediateCall;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.*;

/**
 * @author michael.golubev
 */
public class ThreadInvocationHandler implements InvocationHandler {

  private static final Logger LOG = Logger.getInstance(ThreadInvocationHandler.class);

  private final ExecutorService myTaskExecutor;
  private final ClassLoader myCallerClassLoader;
  private final Object myTarget;
  private final ChildWrapperCreator myPreWrapperFactory;

  public ThreadInvocationHandler(ExecutorService taskExecutor, ClassLoader callerClassLoader, Object target,
                                 @Nullable ChildWrapperCreator preWrapperCreator) {
    myTaskExecutor = taskExecutor;
    myCallerClassLoader = callerClassLoader;
    myTarget = target;
    myPreWrapperFactory = preWrapperCreator;
  }

  @Override
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    final Callable<Object> taskCallable = () -> {
      try {
        return method.invoke(myTarget, args);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOG.error(e);
        return null;
      }
    };

    try {
      boolean immediateCall = method.getAnnotation(ImmediateCall.class) != null;

      boolean childCall = method.getAnnotation(ChildCall.class) != null;
      if (childCall) {
        Object child = immediateCall ? taskCallable.call() : executeAndWait(taskCallable);
        if (child == null) {
          return null;
        }

        Object result;
        Class<?> childClass = child.getClass();
        if (childClass.isArray()) {
          Class<?> componentType = childClass.getComponentType();
          int length = Array.getLength(child);
          result = Array.newInstance(componentType, length);
          for (int i = 0; i < length; i++) {
            Array.set(result, i, createChildProxy(Array.get(child, i)));
          }
        }
        else {
          result = createChildProxy(child);
        }

        return result;
      }

      if (immediateCall || StringUtil.equals(method.getName(), "toString")) {
        return taskCallable.call();
      }

      boolean asyncCall = method.getAnnotation(AsyncCall.class) != null;

      if (asyncCall) {
        myTaskExecutor.execute(() -> {
          try {
            taskCallable.call();
          }
          catch (Exception e) {
            LOG.error(e); // should never happen
          }
        });
        return null;
      }
      else {
        return executeAndWait(taskCallable);
      }
    }
    finally {
      boolean finalCall = method.getAnnotation(FinalCall.class) != null;
      if (finalCall) {
        myTaskExecutor.shutdownNow();
      }
    }
  }

  private Object executeAndWait(Callable<Object> taskCallable) throws Throwable {
    Object child;
    Future<Object> future = myTaskExecutor.submit(taskCallable);
    try {
      child = future.get();
    }
    catch (InterruptedException e) {
      throw new CancellationException("Operation cancelled");
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      throw cause == null ? e : cause;
    }
    return child;
  }

  private Object createChildProxy(Object child) {
    Class<?> childClass = child.getClass();
    Class<?>[] childInterfaces = childClass.getInterfaces();
    LOG.assertTrue(childInterfaces.length == 1, "Child class is expected to implement single child interface");
    Class<?> childInterface = childInterfaces[0];

    Class<?> callerChildInterface;
    try {
      callerChildInterface = myCallerClassLoader.loadClass(childInterface.getName());
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
      return null;
    }

    Object preWrappedChild;
    if (myPreWrapperFactory == null) {
      preWrappedChild = child;
    }
    else {
      preWrappedChild = ReflectionUtil.proxy(callerChildInterface, myPreWrapperFactory.createWrapperInvocationHandler(child));
    }

    return ReflectionUtil.proxy(callerChildInterface,
                                new ThreadInvocationHandler(myTaskExecutor, myCallerClassLoader, preWrappedChild, myPreWrapperFactory));
  }
}
