package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XBreakpointFileGroup extends XBreakpointGroup {
  private final VirtualFile myFile;

  public XBreakpointFileGroup(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Nullable
  public Icon getIcon(final boolean isOpen) {
    return myFile.getIcon();
  }

  @NotNull
  public String getName() {
    return myFile.getPresentableUrl();
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
