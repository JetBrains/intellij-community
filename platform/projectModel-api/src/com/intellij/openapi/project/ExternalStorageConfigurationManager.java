// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.components.ServiceManager;

public interface ExternalStorageConfigurationManager {
  static ExternalStorageConfigurationManager getInstance(Project project) {
    return ServiceManager.getService(project, ExternalStorageConfigurationManager.class);
  }

  boolean isEnabled();
  void setEnabled(boolean value);
}
