package com.intellij.remoteServer.agent;

import com.intellij.openapi.components.ServiceManager;

import java.io.File;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class RemoteAgentManager {

  public static RemoteAgentManager getInstance() {
    return ServiceManager.getService(RemoteAgentManager.class);
  }

  public abstract <T extends RemoteAgent> T createAgent(RemoteAgentProxyFactory agentProxyFactory,
                                                        List<File> instanceLibraries,
                                                        List<Class<?>> commonJarClasses,
                                                        String specificsRuntimeModuleName,
                                                        String specificsBuildJarPath,
                                                        Class<T> agentInterface,
                                                        String agentClassName,
                                                        Class<?> pluginClass) throws Exception;

  public abstract RemoteAgentProxyFactory createReflectiveThreadProxyFactory(ClassLoader callerClassLoader);
}
