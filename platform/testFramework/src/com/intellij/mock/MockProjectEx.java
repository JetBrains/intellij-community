// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManagerEx;
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
    return app == null || app instanceof ComponentManagerEx ? null : app.getPicoContainer();
  }

  @Override
  public void setProjectName(@NotNull String name) {
  }

  @NotNull
  @Override
  public final Disposable getEarlyDisposable() {
    return this;
  }
}