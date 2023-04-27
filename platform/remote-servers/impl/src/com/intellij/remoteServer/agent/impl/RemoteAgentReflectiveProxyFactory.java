// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author michael.golubev
 */
public class RemoteAgentReflectiveProxyFactory extends RemoteAgentProxyFactoryBase {
  private static final Logger LOG = Logger.getInstance(RemoteAgentReflectiveProxyFactory.class);

  private final RemoteAgentClassLoaderCache myClassLoaderCache;

  public RemoteAgentReflectiveProxyFactory(@Nullable RemoteAgentClassLoaderCache classLoaderCache,
                                           CallerClassLoaderProvider callerClassLoaderProvider) {
    super(callerClassLoaderProvider);
    myClassLoaderCache = classLoaderCache;
  }

  @Override
  protected ClassLoader createAgentClassLoader(URL[] agentLibraryUrls) throws Exception {
    Set<URL> urls = new HashSet<>();
    urls.addAll(Arrays.asList(agentLibraryUrls));

    return myClassLoaderCache == null
           ? RemoteAgentClassLoaderCache.createClassLoaderWithoutApplicationParent(urls)
           : myClassLoaderCache.getOrCreateClassLoader(urls);
  }

  @Override
  protected InvocationHandler createInvocationHandler(Object agentImpl, ClassLoader agentClassLoader, ClassLoader callerClassLoader) {
    return new ReflectiveInvocationHandler(agentImpl, agentClassLoader, callerClassLoader);
  }

  private static class ReflectiveInvocationHandler implements InvocationHandler {

    private final Object myTarget;
    private final ClassLoader myTargetClassLoader;
    private final ClassLoader mySourceClassLoader;

    ReflectiveInvocationHandler(Object target, ClassLoader targetClassLoader, ClassLoader sourceClassLoader) {
      myTarget = target;
      myTargetClassLoader = targetClassLoader;
      mySourceClassLoader = sourceClassLoader;
    }

    @Nullable
    @Override
    public Object invoke(Object proxy, final Method method, final Object[] args) {
      ClassLoader initialClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(myTargetClassLoader);

        Class<?>[] parameterTypes = method.getParameterTypes();
        Class<?>[] delegateParameterTypes = new Class<?>[parameterTypes.length];

        Object[] delegateArgs = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          Mirror parameterMirror = new Mirror(parameterTypes[i], args[i], mySourceClassLoader, myTargetClassLoader);
          delegateParameterTypes[i] = parameterMirror.getMirrorType();
          delegateArgs[i] = parameterMirror.getMirrorValue();
        }

        Method delegateMethod = myTarget.getClass().getMethod(method.getName(), delegateParameterTypes);
        delegateMethod.setAccessible(true);

        Object result = delegateMethod.invoke(myTarget, delegateArgs);
        Mirror resultMirror = new Mirror(delegateMethod.getReturnType(), result, myTargetClassLoader, mySourceClassLoader);
        return resultMirror.getMirrorValue();
      }
      catch (IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
        LOG.error(e);
        return null;
      }
      finally {
        Thread.currentThread().setContextClassLoader(initialClassLoader);
      }
    }
  }

  private static class Mirror {

    private final Class<?> myMirrorType;

    private final Object myMirrorValue;

    Mirror(Class<?> type, Object value, ClassLoader classLoader, ClassLoader mirrorClassLoader) throws ClassNotFoundException {
      if (type.isArray()) {
        Class<?> componentType = type.getComponentType();
        Mirror componentMirror = new Mirror(componentType, null, classLoader, mirrorClassLoader);
        int length = value == null ? 0 : Array.getLength(value);
        Object mirrorValue = Array.newInstance(componentMirror.getMirrorType(), length);
        for (int i = 0; i < length; i++) {
          Mirror itemMirror = new Mirror(componentType, Array.get(value, i), classLoader, mirrorClassLoader);
          Array.set(mirrorValue, i, itemMirror.getMirrorValue());
        }
        myMirrorType = mirrorValue.getClass();
        myMirrorValue = value == null ? null : mirrorValue;
      }
      else if (type.isEnum()) {
        @SuppressWarnings("unchecked")
        Class<? extends Enum> mirroredEnum = (Class<? extends Enum>)mirrorClassLoader.loadClass(type.getName());
        myMirrorType = mirroredEnum;
        //noinspection unchecked
        myMirrorValue = value == null ? null : Enum.valueOf(mirroredEnum, ((Enum<?>)value).name());
      }
      else if (type.isInterface()) {
        myMirrorType = mirrorClassLoader.loadClass(type.getName());
        myMirrorValue = value == null ? null
                                      : ReflectionUtil.proxy(myMirrorType,
                                                             new ReflectiveInvocationHandler(value, classLoader, mirrorClassLoader));
      }
      else {
        myMirrorType = type;
        myMirrorValue = value;
      }
    }

    public Class<?> getMirrorType() {
      return myMirrorType;
    }

    public Object getMirrorValue() {
      return myMirrorValue;
    }
  }
}
