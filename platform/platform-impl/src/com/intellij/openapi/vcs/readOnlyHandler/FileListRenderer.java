package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class FileListRenderer extends ColoredListCellRenderer {
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    // paint selection only as a focus rectangle
    mySelected = false;
    setBackground(null);
    VirtualFile vf = (VirtualFile) value;
    setIcon(vf.getIcon());
    append(vf.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    VirtualFile parent = vf.getParent();
    if (parent != null) {
      append(" (" + FileUtil.toSystemDependentName(parent.getPath()) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
