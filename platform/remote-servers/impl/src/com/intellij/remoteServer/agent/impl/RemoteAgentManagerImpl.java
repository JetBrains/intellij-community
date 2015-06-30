package com.intellij.remoteServer.agent.impl;

import com.intellij.remoteServer.agent.RemoteAgent;
import com.intellij.remoteServer.agent.RemoteAgentManager;
import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import com.intellij.util.Base64;
import com.intellij.util.PathUtil;
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
                                               List<Class<?>> commonJarClasses,
                                               RuntimeModuleId specificsRuntimeModuleId,
                                               Class<T> agentInterface,
                                               String agentClassName,
                                               Class<?> pluginClass) throws Exception {

    List<Class<?>> allCommonJarClasses = new ArrayList<Class<?>>();
    allCommonJarClasses.addAll(commonJarClasses);
    allCommonJarClasses.add(RemoteAgent.class);
    allCommonJarClasses.add(Base64.class);
    allCommonJarClasses.add(agentInterface);

    List<File> libraries = new ArrayList<File>();
    libraries.addAll(instanceLibraries);

    for (Class<?> clazz : allCommonJarClasses) {
      libraries.add(new File(PathUtil.getJarPathForClass(clazz)));
    }

    for (String path : PlatformLoader.getInstance().getRepository().getModuleRootPaths(specificsRuntimeModuleId)) {
      libraries.add(new File(path));
    }

    return agentProxyFactory.createProxy(libraries, agentInterface, agentClassName);
  }

  public RemoteAgentProxyFactory createReflectiveThreadProxyFactory(ClassLoader callerClassLoader) {
    return new RemoteAgentReflectiveThreadProxyFactory(myClassLoaderCache, callerClassLoader);
  }
}
