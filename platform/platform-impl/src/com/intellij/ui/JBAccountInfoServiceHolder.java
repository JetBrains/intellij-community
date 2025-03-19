// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class JBAccountInfoServiceHolder {

  static final JBAccountInfoService INSTANCE;

  static {
    JBAccountInfoService service;
    try {
      service = ServiceLoader.load(JBAccountInfoService.class, JBAccountInfoService.class.getClassLoader()).findFirst().orElse(null);
    }
    catch (ServiceConfigurationError ignored) {
      service = null;
    }
    INSTANCE = service;
  }
}
