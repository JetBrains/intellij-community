// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public abstract class ProjectLoadingErrorsNotifier {

  public static ProjectLoadingErrorsNotifier getInstance(@NotNull Project project) {
    return project.getService(ProjectLoadingErrorsNotifier.class);
  }

  public abstract void registerError(@NotNull ConfigurationErrorDescription errorDescription);

  public abstract void registerErrors(@NotNull Collection<? extends ConfigurationErrorDescription> errorDescriptions);
}
