/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.application.ApplicationManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author irengrig
 */
public class AssertProxy<T> {
  private final T myProxy;
  private final T myVictim;
  private final static Runnable AWT_ASSERTION = new Runnable() {
    @Override
    public void run() {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
  };

  public static<T> T create(final T victim, final Runnable assertor) {
    return new AssertProxy<T>(victim, assertor).getProxy();
  }

  public static<T> T createAWTAccess(final T victim) {
    return new AssertProxy<T>(victim, AWT_ASSERTION).getProxy();
  }

  private AssertProxy(final T victim, final Runnable assertor) {
    myVictim = victim;
    final Class<T> clazz = (Class<T>) victim.getClass();
    assert clazz.isInterface();

    myProxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(),
                                        new Class[]{clazz},
                                        new InvocationHandler() {
                                          @Override
                                          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                            assertor.run();
                                            method.setAccessible(true);
                                            return method.invoke(myVictim, args);
                                          }
                                        });
  }

  public T getProxy() {
    return myProxy;
  }
}
