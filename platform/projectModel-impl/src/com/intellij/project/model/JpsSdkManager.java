// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.project.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.jps.model.library.JpsLibrary;

public abstract class JpsSdkManager {
  public static JpsSdkManager getInstance() {
    return ApplicationManager.getApplication().getService(JpsSdkManager.class);
  }

  public abstract Sdk getSdk(JpsLibrary library);
}
