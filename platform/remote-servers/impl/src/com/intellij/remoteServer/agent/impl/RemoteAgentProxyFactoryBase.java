// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.agent.impl;

import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import com.intellij.remoteServer.agent.impl.util.UrlCollector;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * @author michael.golubev
 */
@ApiStatus.Internal
public abstract class RemoteAgentProxyFactoryBase implements RemoteAgentProxyFactory {

  private final CallerClassLoaderProvider myCallerClassLoaderProvider;

  public RemoteAgentProxyFactoryBase(CallerClassLoaderProvider callerClassLoaderProvider) {
    myCallerClassLoaderProvider = callerClassLoaderProvider;
  }

  @Override
  public <T> T createProxy(List<Path> libraries, Class<T> agentInterface, String agentClassName) throws Exception {
    ClassLoader callerClassLoader = myCallerClassLoaderProvider.getCallerClassLoader(agentInterface);
    ClassLoader agentClassLoader = createAgentClassLoader(libraries);
    Object agentImpl = agentClassLoader.loadClass(agentClassName).getDeclaredConstructor().newInstance();
    return ReflectionUtil.proxy(agentInterface, createInvocationHandler(agentImpl, agentClassLoader, callerClassLoader));
  }

  protected ClassLoader createAgentClassLoader(List<Path> libraries) throws Exception {
    return createAgentClassLoader(new UrlCollector().collect(libraries));
  }

  protected abstract ClassLoader createAgentClassLoader(URL[] agentLibraryUrls) throws Exception;

  protected abstract InvocationHandler createInvocationHandler(Object agentImpl,
                                                               ClassLoader agentClassLoader,
                                                               ClassLoader callerClassLoader);
}
