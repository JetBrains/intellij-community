package com.intellij.remoteServer.agent.impl;

import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import com.intellij.remoteServer.agent.impl.util.UrlCollector;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class RemoteAgentProxyFactoryBase implements RemoteAgentProxyFactory {

  private final CallerClassLoaderProvider myCallerClassLoaderProvider;

  public RemoteAgentProxyFactoryBase(CallerClassLoaderProvider callerClassLoaderProvider) {
    myCallerClassLoaderProvider = callerClassLoaderProvider;
  }

  @Override
  public <T> T createProxy(List<File> libraries, Class<T> agentInterface, String agentClassName) throws Exception {
    ClassLoader callerClassLoader = myCallerClassLoaderProvider.getCallerClassLoader(agentInterface);
    ClassLoader agentClassLoader = createAgentClassLoader(libraries);
    Object agentImpl = agentClassLoader.loadClass(agentClassName).getDeclaredConstructor().newInstance();
    return agentInterface.cast(Proxy.newProxyInstance(agentInterface.getClassLoader(),
                                                      new Class[]{agentInterface},
                                                      createInvocationHandler(agentImpl, agentClassLoader, callerClassLoader)));
  }

  protected ClassLoader createAgentClassLoader(List<File> libraries) throws Exception {
    return createAgentClassLoader(new UrlCollector().collect(libraries));
  }

  protected abstract ClassLoader createAgentClassLoader(URL[] agentLibraryUrls) throws Exception;

  protected abstract InvocationHandler createInvocationHandler(Object agentImpl,
                                                               ClassLoader agentClassLoader,
                                                               ClassLoader callerClassLoader);
}
