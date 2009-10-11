/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public abstract class WeakListener<Src, Listener> implements InvocationHandler{
  private final WeakReference<Listener> myDelegate;
  private Src mySource;

  protected WeakListener(Src source, Class<Listener> listenerInterface, Listener listenerImpl) {
    mySource = source;
    myDelegate = new WeakReference<Listener>(listenerImpl);
    final ClassLoader classLoader = listenerImpl.getClass().getClassLoader();
    final Listener proxy = (Listener)Proxy.newProxyInstance(classLoader, new Class[]{listenerInterface}, this);
    addListener(source, proxy);
  }

  protected abstract void addListener(Src source, Listener listener);

  protected abstract void removeListener(Src source, Listener listener);

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
