// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PathMappingProvider {
  public static final ExtensionPointName<PathMappingProvider> EP_NAME = ExtensionPointName.create("com.intellij.remote.pathMappingProvider");

  public static List<PathMappingProvider> getSuitableMappingProviders(@Nullable RemoteSdkAdditionalData data) {
    return ContainerUtil.filter(EP_NAME.getExtensions(), provider -> provider.accepts(data));
  }

  public abstract @NotNull String getProviderPresentableName(@NotNull RemoteSdkAdditionalData data);

  public abstract boolean accepts(@Nullable RemoteSdkAdditionalData data);

  public abstract @NotNull PathMappingSettings getPathMappingSettings(@NotNull Project project, @NotNull RemoteSdkAdditionalData data);
}
