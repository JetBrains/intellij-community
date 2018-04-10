/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remoteServer.agent.impl;

import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;

/**
 * @author michael.golubev
 */
public class RemoteAgentThreadProxyCreator {

  private final CallerClassLoaderProvider myCallerClassLoaderProvider;
  private final ChildWrapperCreator myPreWrapperCreator;

  public RemoteAgentThreadProxyCreator(CallerClassLoaderProvider callerClassLoaderProvider,
                                       @Nullable ChildWrapperCreator preWrapperCreator) {
    myPreWrapperCreator = preWrapperCreator;
    myCallerClassLoaderProvider = callerClassLoaderProvider;
  }

  public <T> T createProxy(Class<T> agentInterface, T agentInstance) {
    ClassLoader callerClassLoader = myCallerClassLoaderProvider.getCallerClassLoader(agentInterface);

    return agentInterface.cast(Proxy.newProxyInstance(callerClassLoader,
                                                      new Class[]{agentInterface},
                                                      new ThreadInvocationHandler(
                                                        SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
                                                          "RemoteAgentThreadProxyCreator Pool"),
                                                        callerClassLoader, agentInstance,
                                                        myPreWrapperCreator
                                                      )));
  }
}
