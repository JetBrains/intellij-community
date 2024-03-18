// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;

import java.net.ProxySelector;

public interface IoService {
  ProxySelector getProxySelector(String pacUrlForUse);

  static IoService getInstance() {
    return ApplicationManager.getApplication().getService(IoService.class);
  }
}
