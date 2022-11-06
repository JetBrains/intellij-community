// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Experimental
public interface ProjectTypesProvider {
  ExtensionPointName<ProjectTypesProvider> EP_NAME = ExtensionPointName.create("com.intellij.projectTypesProvider");

  Collection<ProjectType> inferProjectTypes(@NotNull Project project);
}
