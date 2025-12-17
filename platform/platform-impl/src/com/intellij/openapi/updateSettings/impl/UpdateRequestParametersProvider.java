// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Url;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@ApiStatus.Internal
public interface UpdateRequestParametersProvider {
  void amendUpdateRequest(@NotNull Map<String, String> parameters);

  static @NotNull Url passUpdateParameters(@NotNull Url url) {
    if (URLUtil.FILE_PROTOCOL.equals(url.getScheme())) {
      return url;
    }

    var parameters = new LinkedHashMap<String, String>();
    ApplicationManager.getApplication()
      .getService(UpdateRequestParametersProvider.class)
      .amendUpdateRequest(parameters);
    return url.addParameters(parameters);
  }
}
