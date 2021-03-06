// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProjectNameProvider {
  ExtensionPointName<ProjectNameProvider> EP_NAME = new ExtensionPointName<>("com.intellij.projectNameProvider");

  @Nullable String getDefaultName(@NotNull Project project);
}
