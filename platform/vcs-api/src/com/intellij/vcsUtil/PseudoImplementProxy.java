/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.vcsUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/22/12
 * Time: 10:58 AM
 */
public class PseudoImplementProxy {
  public static <T, Impl> T create(final Class<T> theInterface, final Impl implementation) {
    checkMethodsExist(theInterface, implementation);
    final Class<?> implementationClass = implementation.getClass();
    return (T) Proxy.newProxyInstance(theInterface.getClassLoader(), new Class[]{theInterface},
                                      new InvocationHandler() {
                                        @Override
                                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                          final Method implementationClassMethod =
                                            implementationClass.getMethod(method.getName(), method.getParameterTypes());
                                          implementationClassMethod.setAccessible(true);
                                          return implementationClassMethod.invoke(implementation, args);
                                        }
                                      });
  }

  private static <T, Impl> void checkMethodsExist(Class<T> theInterface, Impl implementation) {
    final Class<?> implementationClass = implementation.getClass();
    final Method[] methods = theInterface.getDeclaredMethods();
    for (Method method : methods) {
      try {
        implementationClass.getMethod(method.getName(), method.getParameterTypes());
      }
      catch (NoSuchMethodException e) {
        throw new UnsupportedOperationException(e);
      }
    }
  }
}
