// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XBreakpointFileGroup extends XBreakpointGroup {
  private final VirtualFile myFile;

  public XBreakpointFileGroup(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  public @Nullable Icon getIcon(final boolean isOpen) {
    return VirtualFilePresentation.getIcon(myFile);
  }

  @Override
  public @NotNull String getName() {
    return myFile.getPresentableUrl();
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof XBreakpointFileGroup && myFile.equals(((XBreakpointFileGroup)obj).myFile);
  }
}
