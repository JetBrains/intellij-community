// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remoteServer.agent.RemoteAgent;
import com.intellij.remoteServer.agent.RemoteAgentManager;
import com.intellij.remoteServer.agent.RemoteAgentProxyFactory;
import com.intellij.util.Base64;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@ApiStatus.Internal
public class RemoteAgentManagerImpl extends RemoteAgentManager {

  private final RemoteAgentClassLoaderCache myClassLoaderCache = new RemoteAgentClassLoaderCache();

  @Override
  public <T extends RemoteAgent> T createAgent(RemoteAgentProxyFactory agentProxyFactory,
                                               List<Path> instanceLibraries,
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
    private final List<Path> myInstanceLibraries = new ArrayList<>();
    private final List<Path> myModuleDependencies = new ArrayList<>();

    private final RemoteAgentProxyFactory myAgentProxyFactory;
    private final Class<T> myAgentInterface;

    private final Path myPluginPath;
    private final String myAllPluginsRoot;

    AgentBuilderImpl(@NotNull RemoteAgentProxyFactory agentProxyFactory,
                            @NotNull Class<T> agentInterface,
                            @NotNull Class<?> pluginClass) {
      myAgentProxyFactory = agentProxyFactory;
      myAgentInterface = agentInterface;

      File plugin = new File(PathUtil.getJarPathForClass(pluginClass));
      myPluginPath = plugin.toPath();
      myAllPluginsRoot = plugin.getParent();
    }

    @Override
    public T buildAgent(@NotNull String agentClassName) throws Exception {
      @NotNull List<Path> libraries = listLibraryFiles();
      return myAgentProxyFactory.createProxy(libraries, myAgentInterface, agentClassName);
    }

    private @NotNull List<Path> listLibraryFiles() {
      List<Path> result = new ArrayList<>(myInstanceLibraries);

      List<Class<?>> allRtClasses = new ArrayList<>(myRtClasses);
      allRtClasses.add(RemoteAgent.class);
      allRtClasses.add(Base64.class);
      allRtClasses.add(myAgentInterface);

      for (Class<?> clazz : allRtClasses) {
        result.add(Path.of(PathUtil.getJarPathForClass(clazz)));
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
    public Builder<T> withInstanceLibraries(List<Path> libraries) {
      myInstanceLibraries.addAll(libraries);
      return this;
    }

    @Override
    public Builder<T> withModuleDependency(@NotNull String runtimeModuleName, @NotNull String buildPathToJar) {
      myModuleDependencies.add(resolveModuleDependency(runtimeModuleName, buildPathToJar));
      return this;
    }

    private @NotNull Path resolveModuleDependency(@NotNull String runtimeModuleName, @NotNull String buildPathToJar) {
      Path pluginsRoot = Path.of(myAllPluginsRoot);
      Path specificsModule = pluginsRoot.resolve(runtimeModuleName);
      if (specificsModule.toFile().exists()) {
        return specificsModule;
      }

      Path specificsJar = pluginsRoot.resolve("lib").resolve(FileUtil.toSystemDependentName(buildPathToJar));
      if (specificsJar.toFile().exists()) {
        return specificsJar;
      }

      Path jarCacheSpecificsJar = resolveJarCacheDependency(buildPathToJar);
      if (jarCacheSpecificsJar != null) {
        return jarCacheSpecificsJar;
      }

      return specificsModule;
    }

    private Path resolveJarCacheDependency(@NotNull String buildPathToJar) {
      String jarName = Path.of(FileUtil.toSystemDependentName(buildPathToJar)).getFileName().toString();
      Path cacheEntriesRoot = findJarCacheEntriesRoot();
      if (cacheEntriesRoot == null || jarName.isEmpty()) {
        return null;
      }

      String cacheSuffix = "__" + jarName;
      try (Stream<Path> paths = Files.walk(cacheEntriesRoot)) {
        return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(cacheSuffix))
          .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
          .orElse(null);
      }
      catch (IOException ignored) {
        return null;
      }
    }

    private Path findJarCacheEntriesRoot() {
      for (Path current = myPluginPath.getParent(); current != null; current = current.getParent()) {
        if ("entries".equals(current.getFileName() != null ? current.getFileName().toString() : null)) {
          return current;
        }
      }
      return null;
    }
  }
}
