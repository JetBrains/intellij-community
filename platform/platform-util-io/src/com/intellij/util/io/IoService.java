// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.net.ProxySelector;

@ApiStatus.Internal
public interface IoService {
  @Nullable ProxySelector getProxySelector(String pacUrlForUse);

  static IoService getInstance() {
    return ApplicationManager.getApplication().getService(IoService.class);
  }
}
