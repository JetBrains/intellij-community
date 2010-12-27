/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author irengrig
 *         Date: 12/17/10
 *         Time: 2:21 PM
 */
public class IllegalStateProxy {
  public static final Object IDENTITY = new Object();

  private static final InvocationHandler HANDLER = new InvocationHandler() {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("equals".equals(method.getName())) {
        return IDENTITY.equals(args[0]);
      }
      throw new IllegalStateException();
    }
  };

  private IllegalStateProxy() {
  }

  public static<T> T create(final Class<T> clazz) {
    return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, HANDLER);
  }
}
