// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service({Service.Level.APP, Service.Level.PROJECT})
public final class AntDisposable implements Disposable {
  @Override
  public void dispose() {
  }

  public static AntDisposable getInstance() {
    return ApplicationManager.getApplication().getService(AntDisposable.class);
  }

  public static AntDisposable getInstance(Project project) {
    return project.getService(AntDisposable.class);
  }
}
