package com.intellij.remoteServer.agent.impl;

import com.intellij.remoteServer.agent.RemoteAgent;
import com.intellij.remoteServer.agent.RemoteAgentManager;
import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import org.jetbrains.platform.loader.PlatformLoader;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author michael.golubev
 */
public class RemoteAgentManagerImpl extends RemoteAgentManager {

  private final RemoteAgentClassLoaderCache myClassLoaderCache = new RemoteAgentClassLoaderCache();

  @Override
  public <T extends RemoteAgent> T createAgent(RemoteAgentProxyFactory agentProxyFactory,
                                               List<File> instanceLibraries,
                                               RuntimeModuleId specificsRuntimeModuleId,
                                               Class<T> agentInterface,
                                               String agentClassName) throws Exception {

    List<File> libraries = new ArrayList<File>();
    libraries.addAll(instanceLibraries);
    for (String path : PlatformLoader.getInstance().getRepository().getModuleClasspath(specificsRuntimeModuleId)) {
      libraries.add(new File(path));
    }

    return agentProxyFactory.createProxy(libraries, agentInterface, agentClassName);
  }

  public RemoteAgentProxyFactory createReflectiveThreadProxyFactory(ClassLoader callerClassLoader) {
    return new RemoteAgentReflectiveThreadProxyFactory(myClassLoaderCache, callerClassLoader);
  }
}
