// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Url;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface UpdateRequestParametersProvider {
  @NotNull Url amendUpdateRequest(@NotNull Url url);

  static @NotNull Url passUpdateParameters(@NotNull Url url) {
    UpdateRequestParametersProvider provider = ApplicationManager.getApplication().getService(UpdateRequestParametersProvider.class);
    return provider.amendUpdateRequest(url);
  }
}
