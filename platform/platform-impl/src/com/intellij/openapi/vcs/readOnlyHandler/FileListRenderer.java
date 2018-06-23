// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FileListRenderer extends ColoredListCellRenderer<VirtualFile> {
  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends VirtualFile> list, VirtualFile vf, int index, boolean selected, boolean hasFocus) {
    // paint selection only as a focus rectangle
    mySelected = false;
    setBackground(null);
    setIcon(VirtualFilePresentation.getIcon(vf));
    append(vf.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    VirtualFile parent = vf.getParent();
    if (parent != null) {
      append(" (" + FileUtil.toSystemDependentName(parent.getPath()) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}