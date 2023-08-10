// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.agent.impl;

import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.Nullable;

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

    return ReflectionUtil.proxy(callerClassLoader,
                                agentInterface,
                                new ThreadInvocationHandler(
                                  SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
                                    "RemoteAgentThreadProxyCreator Pool"),
                                  callerClassLoader, agentInstance,
                                  myPreWrapperCreator
                                ));
  }
}
