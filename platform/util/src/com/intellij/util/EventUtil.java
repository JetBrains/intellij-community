/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EventListener;

public class EventUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EventUtil");

  public static <T extends EventListener> T createWeakListener(final Class<T> listenerClass, T listener) {
    final WeakReference reference = new WeakReference(listener);

    InvocationHandler handler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object o = reference.get();
        if (o == null) {
          //noinspection HardCodedStringLiteral
          if ("equals".equals(method.getName())) return Boolean.FALSE;
          return null;
        }

        try{
          Object result = method.invoke(o, args);
          return result;
        }
        catch(IllegalAccessException e){
          LOG.error(e);
        }
        catch(IllegalArgumentException e){
          LOG.error(e);
        }
        catch(InvocationTargetException e){
          throw e.getTargetException();
        }

        return null;
      }
    };

    return (T)Proxy.newProxyInstance(listenerClass.getClassLoader(),
      new Class[]{listenerClass},
      handler
    );
  }
}
