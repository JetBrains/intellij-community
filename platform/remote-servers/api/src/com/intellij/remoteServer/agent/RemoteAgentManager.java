// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.agent;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class RemoteAgentManager {

  public static RemoteAgentManager getInstance() {
    return ApplicationManager.getApplication().getService(RemoteAgentManager.class);
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

  public abstract <T extends RemoteAgent> Builder<T> createAgentBuilder(@NotNull RemoteAgentProxyFactory agentProxyFactory,
                                                                        @NotNull Class<T> agentInterface,
                                                                        @NotNull Class<?> pluginClass);

  public abstract static class Builder<T extends RemoteAgent> {
    public abstract Builder<T> withInstanceLibraries(@NotNull List<File> libraries);

    /**
     * @param rtClass "independent" class from *.rt module, without dependency to the rest of IDEA. Since the whole module
     *                  will be added to agent dependencies, passing one class per rt-module is enough
     */
    public abstract Builder<T> withRtDependency(@NotNull Class<?> rtClass);

    public final Builder<T> withRtDependencies(@NotNull List<Class<?>> rtClasses) {
      for (Class<?> next : rtClasses) {
        withRtDependency(next);
      }
      return this;
    }

    public abstract Builder<T> withModuleDependency(@NotNull String runtimeModuleName, @NotNull String buildPathToJar);

    public abstract T buildAgent(@NotNull String agentClassName) throws Exception;
  }
}
