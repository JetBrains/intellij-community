// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;

@ApiStatus.Internal
public final class FileChooserActionUtil {
  private FileChooserActionUtil() {
  }

  public static Path getDesktopDir() {
    return GotoDesktopDirAction.getDesktopDirectory();
  }
}
