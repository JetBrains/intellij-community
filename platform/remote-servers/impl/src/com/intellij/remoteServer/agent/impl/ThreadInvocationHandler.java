package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.agent.annotation.AsyncCall;
import com.intellij.remoteServer.agent.annotation.ChildCall;
import com.intellij.remoteServer.agent.annotation.FinalCall;
import com.intellij.remoteServer.agent.annotation.ImmediateCall;
import com.intellij.remoteServer.agent.impl.util.FinalTask;
import com.intellij.remoteServer.agent.impl.util.SequentialTaskExecutor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.concurrent.Callable;

/**
 * @author michael.golubev
 */
public class ThreadInvocationHandler implements InvocationHandler {

  private static final Logger LOG = Logger.getInstance("#" + ThreadInvocationHandler.class.getName());

  private final SequentialTaskExecutor myTaskExecutor;
  private final ClassLoader myCallerClassLoader;
  private final Object myTarget;
  private final ChildWrapperCreator myPreWrapperFactory;

  public ThreadInvocationHandler(SequentialTaskExecutor taskExecutor, ClassLoader callerClassLoader, Object target) {
    this(taskExecutor, callerClassLoader, target, null);
  }

  public ThreadInvocationHandler(SequentialTaskExecutor taskExecutor, ClassLoader callerClassLoader, Object target,
                                 @Nullable ChildWrapperCreator preWrapperCreator) {
    myTaskExecutor = taskExecutor;
    myCallerClassLoader = callerClassLoader;
    myTarget = target;
    myPreWrapperFactory = preWrapperCreator;
  }

  @Override
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    final Callable<Object> taskCallable = new Callable<Object>() {

      @Override
      public Object call() {
        try {
          return method.invoke(myTarget, args);
        }
        catch (IllegalAccessException e) {
          LOG.error(e);
          return null;
        }
        catch (InvocationTargetException e) {
          LOG.error(e);
          return null;
        }
      }
    };

    try {
      boolean immediateCall = method.getAnnotation(ImmediateCall.class) != null;

      boolean childCall = method.getAnnotation(ChildCall.class) != null;
      if (childCall) {
        Object child = immediateCall ? taskCallable.call() : myTaskExecutor.queueAndWaitTask(taskCallable);
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
        myTaskExecutor.queueTask(new Runnable() {

          @Override
          public void run() {
            try {
              taskCallable.call();
            }
            catch (Exception e) {
              LOG.error(e); // should never happen
            }
          }
        });
        return null;
      }
      else {
        return myTaskExecutor.queueAndWaitTask(taskCallable);
      }
    }
    finally {
      boolean finalCall = method.getAnnotation(FinalCall.class) != null;
      if (finalCall) {
        myTaskExecutor.queueTask(new FinalTask() {

          @Override
          public void run() {

          }
        });
      }
    }
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
      preWrappedChild = Proxy.newProxyInstance(myCallerClassLoader,
                                               new Class[]{callerChildInterface},
                                               myPreWrapperFactory.createWrapperInvocationHandler(child));
    }

    return Proxy.newProxyInstance(myCallerClassLoader,
                                  new Class[]{callerChildInterface},
                                  new ThreadInvocationHandler(myTaskExecutor, myCallerClassLoader, preWrappedChild,
                                                              myPreWrapperFactory));
  }
}
