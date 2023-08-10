// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @Nullable
  public Library getLibrary(final Library library, final String libraryName, final String libraryLevel) {
    return library;
  }

  @Nullable
  public Sdk getSdk(final Sdk sdk, final String sdkName) {
    return sdk;
  }

  public Module getModule(final Module module, final String moduleName) {
    return module;
  }

  @Nullable
  public Sdk getProjectSdk(@NotNull Project project) {
    return ProjectRootManager.getInstance(project).getProjectSdk();
  }

  @Nullable
  public String getProjectSdkName(@NotNull final Project project) {
    return ProjectRootManager.getInstance(project).getProjectSdkName();
  }
}