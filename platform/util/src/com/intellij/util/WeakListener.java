// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public abstract class WeakListener<Src, Listener> implements InvocationHandler{
  private final WeakReference<Listener> myDelegate;
  private Src mySource;

  protected WeakListener(Src source, Class<Listener> listenerInterface, Listener listenerImpl) {
    mySource = source;
    myDelegate = new WeakReference<>(listenerImpl);
    final ClassLoader classLoader = listenerImpl.getClass().getClassLoader();
    final Listener proxy = ReflectionUtil.proxy(classLoader, listenerInterface, this);
    addListener(source, proxy);
  }

  protected abstract void addListener(Src source, Listener listener);

  protected abstract void removeListener(Src source, Listener listener);

  @Override
  public final Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    final Listener listenerImplObject = myDelegate.get();
    if (listenerImplObject == null) { // already collected
      removeListener(mySource, (Listener)proxy);
      mySource = null;
      return null;
    }
    return method.invoke(listenerImplObject, params);
  }
}
