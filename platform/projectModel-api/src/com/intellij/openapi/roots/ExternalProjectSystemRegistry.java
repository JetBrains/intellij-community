// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface ExternalProjectSystemRegistry {
  static ExternalProjectSystemRegistry getInstance() {
    return ApplicationManager.getApplication().getService(ExternalProjectSystemRegistry.class);
  }

  @NotNull
  ProjectModelExternalSource getSourceById(@NotNull String id);

  @Nullable
  ProjectModelExternalSource getExternalSource(@NotNull Module module);

  String EXTERNAL_SYSTEM_ID_KEY = "external.system.id";
}
