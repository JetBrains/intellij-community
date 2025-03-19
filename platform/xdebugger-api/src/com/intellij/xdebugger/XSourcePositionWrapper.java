// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

public abstract class XSourcePositionWrapper implements XSourcePosition {
  protected final XSourcePosition myPosition;

  protected XSourcePositionWrapper(@NotNull XSourcePosition position) {
    myPosition = position;
  }

  @Override
  public final int getLine() {
    return myPosition.getLine();
  }

  @Override
  public final int getOffset() {
    return myPosition.getOffset();
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myPosition.getFile();
  }

  @Override
  public @NotNull Navigatable createNavigatable(@NotNull Project project) {
    return myPosition.createNavigatable(project);
  }
}