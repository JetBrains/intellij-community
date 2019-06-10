// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ex.ProjectEx;
import org.jetbrains.annotations.NotNull;

public class MockProjectEx  extends MockProject implements ProjectEx {
  public MockProjectEx(@NotNull Disposable parentDisposable) {
    super(ApplicationManager.getApplication() != null ? ApplicationManager.getApplication().getPicoContainer() : null, parentDisposable);
  }

  @Override
  public void setProjectName(@NotNull String name) {
  }
}