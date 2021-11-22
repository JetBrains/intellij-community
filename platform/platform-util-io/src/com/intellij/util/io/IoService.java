// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;

import java.net.ProxySelector;

public interface IoService {
  ProxySelector getProxySelector(String pacUrlForUse);

  PowerStatus getPowerStatus();

  static IoService getInstance() {
    return ApplicationManager.getApplication().getService(IoService.class);
  }
}
