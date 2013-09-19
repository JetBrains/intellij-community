package com.intellij.remoteServer.agent.impl;

import com.intellij.remoteServer.agent.impl.util.SequentialTaskExecutor;
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
    final SequentialTaskExecutor taskExecutor = new SequentialTaskExecutor();

    ClassLoader callerClassLoader = myCallerClassLoaderProvider.getCallerClassLoader(agentInterface);

    return agentInterface.cast(Proxy.newProxyInstance(callerClassLoader,
                                                      new Class[]{agentInterface},
                                                      new ThreadInvocationHandler(taskExecutor, callerClassLoader, agentInstance,
                                                                                  myPreWrapperCreator)));
  }
}
