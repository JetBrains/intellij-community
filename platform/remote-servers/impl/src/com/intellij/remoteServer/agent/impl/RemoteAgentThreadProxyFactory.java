package com.intellij.remoteServer.agent.impl;

import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @author michael.golubev
 */
public class RemoteAgentThreadProxyFactory implements RemoteAgentProxyFactory {

  private final RemoteAgentThreadProxyCreator myCreator;
  private final RemoteAgentProxyFactory myDelegate;

  public RemoteAgentThreadProxyFactory(CallerClassLoaderProvider callerClassLoaderProvider, @NotNull RemoteAgentProxyFactory delegate,
                                       @Nullable ChildWrapperCreator preWrapperCreator) {
    myCreator = new RemoteAgentThreadProxyCreator(callerClassLoaderProvider, preWrapperCreator);
    myDelegate = delegate;
  }

  @Override
  public <T> T createProxy(List<File> libraries, Class<T> agentInterface, String agentClassName) throws Exception {
    T agentDelegate = myDelegate.createProxy(libraries, agentInterface, agentClassName);
    return myCreator.createProxy(agentInterface, agentDelegate);
  }
}
