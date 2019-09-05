// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@State(name = "WindowStateProjectService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
final class WindowStateProjectService extends WindowStateServiceImpl {
  WindowStateProjectService(@NotNull Project project) {
    super(project);
  }

  @Override
  Point getDefaultLocationFor(@NotNull String key) {
    Rectangle bounds = getDefaultBoundsFor(key);
    return bounds == null ? null : bounds.getLocation();
  }

  @Override
  Dimension getDefaultSizeFor(@NotNull String key) {
    Rectangle bounds = getDefaultBoundsFor(key);
    return bounds == null ? null : bounds.getSize();
  }

  @Override
  Rectangle getDefaultBoundsFor(@NotNull String key) {
    return null;
  }

  @Override
  boolean getDefaultMaximizedFor(Object object, @NotNull String key) {
    return false;
  }
}
