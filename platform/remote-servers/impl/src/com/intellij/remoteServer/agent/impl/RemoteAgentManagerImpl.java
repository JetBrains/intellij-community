package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remoteServer.agent.RemoteAgent;
import com.intellij.remoteServer.agent.RemoteAgentManager;
import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import com.intellij.util.Base64;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

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
                                               String specificsRuntimeModuleName,
                                               String specificsBuildJarPath,
                                               Class<T> agentInterface,
                                               String agentClassName,
                                               Class<?> pluginClass) throws Exception {

    Builder<T> builder = createAgentBuilder(agentProxyFactory, agentInterface, pluginClass)
      .withRtDependencies(commonJarClasses)
      .withInstanceLibraries(instanceLibraries)
      .withModuleDependency(specificsRuntimeModuleName, specificsBuildJarPath);

    return builder.buildAgent(agentClassName);
  }

  @Override
  public <T extends RemoteAgent> Builder<T> createAgentBuilder(@NotNull RemoteAgentProxyFactory agentProxyFactory,
                                                               @NotNull Class<T> agentInterface,
                                                               @NotNull Class<?> pluginClass) {
    return new AgentBuilderImpl<>(agentProxyFactory, agentInterface, pluginClass);
  }

  @Override
  public RemoteAgentProxyFactory createReflectiveThreadProxyFactory(ClassLoader callerClassLoader) {
    return new RemoteAgentReflectiveThreadProxyFactory(myClassLoaderCache, callerClassLoader);
  }

  private static class AgentBuilderImpl<T extends RemoteAgent> extends Builder<T> {
    private final List<Class<?>> myRtClasses = new ArrayList<>();
    private final List<File> myInstanceLibraries = new ArrayList<>();
    private final List<File> myModuleDependencies = new ArrayList<>();

    private final RemoteAgentProxyFactory myAgentProxyFactory;
    private final Class<T> myAgentInterface;

    private final String myAllPluginsRoot;
    private final boolean myRunningFromSources;

    AgentBuilderImpl(@NotNull RemoteAgentProxyFactory agentProxyFactory,
                            @NotNull Class<T> agentInterface,
                            @NotNull Class<?> pluginClass) {
      myAgentProxyFactory = agentProxyFactory;
      myAgentInterface = agentInterface;

      File plugin = new File(PathUtil.getJarPathForClass(pluginClass));
      myAllPluginsRoot = plugin.getParent();
      myRunningFromSources = plugin.isDirectory();
    }

    @Override
    public T buildAgent(@NotNull String agentClassName) throws Exception {
      List<File> libraries = listLibraryFiles();
      return myAgentProxyFactory.createProxy(libraries, myAgentInterface, agentClassName);
    }

    @NotNull
    private List<File> listLibraryFiles() {
      List<File> result = new ArrayList<>(myInstanceLibraries);

      List<Class<?>> allRtClasses = new ArrayList<>(myRtClasses);
      allRtClasses.add(RemoteAgent.class);
      allRtClasses.add(Base64.class);
      allRtClasses.add(myAgentInterface);

      for (Class<?> clazz : allRtClasses) {
        result.add(new File(PathUtil.getJarPathForClass(clazz)));
      }

      result.addAll(myModuleDependencies);

      return result;
    }

    @Override
    public Builder<T> withRtDependency(@NotNull Class<?> rtClass) {
      myRtClasses.add(rtClass);
      return this;
    }

    @Override
    public Builder<T> withInstanceLibraries(@NotNull List<? extends File> libraries) {
      myInstanceLibraries.addAll(libraries);
      return this;
    }

    @Override
    public Builder<T> withModuleDependency(@NotNull String runtimeModuleName, @NotNull String buildPathToJar) {
      if (myRunningFromSources) {
        File specificsModule = new File(myAllPluginsRoot, runtimeModuleName);
        myModuleDependencies.add(specificsModule);
      }
      else {
        File specificsDir = new File(myAllPluginsRoot, FileUtil.toSystemDependentName(buildPathToJar));
        myModuleDependencies.add(specificsDir);
      }
      return this;
    }
  }
}
