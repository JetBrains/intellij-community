// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public class RootConfigurationAccessor {
  public static final RootConfigurationAccessor DEFAULT_INSTANCE = new RootConfigurationAccessor();

  public @Nullable Library getLibrary(final Library library, final String libraryName, final String libraryLevel) {
    return library;
  }

  public @Nullable Sdk getSdk(final Sdk sdk, final String sdkName) {
    return sdk;
  }

  public Module getModule(final Module module, final String moduleName) {
    return module;
  }

  public @Nullable Sdk getProjectSdk(@NotNull Project project) {
    return ProjectRootManager.getInstance(project).getProjectSdk();
  }

  public @Nullable String getProjectSdkName(final @NotNull Project project) {
    return ProjectRootManager.getInstance(project).getProjectSdkName();
  }
}