package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remoteServer.agent.annotation.AsyncCall;
import com.intellij.remoteServer.agent.annotation.ChildCall;
import com.intellij.remoteServer.agent.annotation.FinalCall;
import com.intellij.remoteServer.agent.annotation.ImmediateCall;
import com.intellij.remoteServer.agent.impl.util.FinalTask;
import com.intellij.remoteServer.agent.impl.util.SequentialTaskExecutor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
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

  private Map<Object, Object> myChild2Wrapped;

  public ThreadInvocationHandler(SequentialTaskExecutor taskExecutor, ClassLoader callerClassLoader, Object target) {
    this(taskExecutor, callerClassLoader, target, null);
  }

  public ThreadInvocationHandler(SequentialTaskExecutor taskExecutor, ClassLoader callerClassLoader, Object target,
                                 @Nullable ChildWrapperCreator preWrapperCreator) {
    myTaskExecutor = taskExecutor;
    myCallerClassLoader = callerClassLoader;
    myTarget = target;
    myPreWrapperFactory = preWrapperCreator;
    myChild2Wrapped = new HashMap<Object, Object>();
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

        Object cached = myChild2Wrapped.get(child);
        if (cached != null) {
          return cached;
        }

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

        Object result = Proxy.newProxyInstance(myCallerClassLoader,
                                               new Class[]{callerChildInterface},
                                               new ThreadInvocationHandler(myTaskExecutor, myCallerClassLoader, preWrappedChild,
                                                                           myPreWrapperFactory));

        myChild2Wrapped.put(child, result);
        return result;
      }

      if (immediateCall) {
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
}
