// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ex.ProjectEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

public class MockProjectEx extends MockProject implements ProjectEx {
  public MockProjectEx(@NotNull Disposable parentDisposable) {
    super(getParentContainer(), parentDisposable);
  }

  private static @Nullable PicoContainer getParentContainer() {
    Application app = ApplicationManager.getApplication();
    return app instanceof MockApplication ? ((MockApplication)app).getPicoContainer() : null;
  }

  @Override
  public void setProjectName(@NotNull String name) {
  }

  @Override
  public final @NotNull Disposable getEarlyDisposable() {
    return this;
  }
}