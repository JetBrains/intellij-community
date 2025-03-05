// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a possibility to obtain {@link ProjectJdkTable} relevant to a particular {@link Project}.
 * It does not mean that the resulting {@link ProjectJdkTable}'s lifetime and storage is bound to a project;
 * rather, we intend to provide a "view" of {@link ProjectJdkTable}.
 */
@ApiStatus.Internal
public interface SdkTableProjectViewProvider {
  @NotNull ProjectJdkTable getSdkTableView();
}
