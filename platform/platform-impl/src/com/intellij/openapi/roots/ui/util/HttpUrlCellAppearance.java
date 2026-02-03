// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.Internal
public final class HttpUrlCellAppearance extends ValidFileCellAppearance {
  public HttpUrlCellAppearance(VirtualFile file) {
    super(file);
  }

  @Override
  protected Icon getIcon() {
    return PlatformIcons.WEB_ICON;
  }
}
