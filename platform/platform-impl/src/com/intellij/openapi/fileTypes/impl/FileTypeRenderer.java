// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.HashSet;
import java.util.Set;

public class FileTypeRenderer extends SimpleListCellRenderer<FileType> {
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private final @Nullable ListModel<? extends FileType> myModel;
  private @Nullable Set<String> myDuplicateDescriptions = null;

  public FileTypeRenderer() {
    myModel = null;
    myDuplicateDescriptions = new HashSet<>();
    Set<String> filter = new HashSet<>();
    for (FileType type : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      String s = type.getDescription();
      if (!filter.add(s)) myDuplicateDescriptions.add(s);
    }
  }

  public FileTypeRenderer(@NotNull ListModel<? extends FileType> model) {
    myModel = model;
    myModel.addListDataListener(new ListDataListener() {
      @Override public void intervalAdded(ListDataEvent e) { myDuplicateDescriptions = null; }
      @Override public void intervalRemoved(ListDataEvent e) { myDuplicateDescriptions = null; }
      @Override public void contentsChanged(ListDataEvent e) { myDuplicateDescriptions = null; }
    });
  }

  @Override
  public void customize(@NotNull JList<? extends FileType> list, FileType value, int index, boolean selected, boolean hasFocus) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    Icon icon = value.getIcon();
    if (icon != null) {
      layeredIcon.setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }
    setIcon(layeredIcon);

    String description = value.getDescription();
    if (isDuplicated(description)) {
      setText(description + " (" + value.getName() + ")");  // NON-NLS (in this case, the name is acceptable)
    }
    else {
      setText(description);
    }
  }

  private boolean isDuplicated(String description) {
    if (myDuplicateDescriptions == null) {
      assert myModel != null;
      myDuplicateDescriptions = new HashSet<>();
      Set<String> filter = new HashSet<>();
      for (int i = 0; i < myModel.getSize(); i++) {
        String s = myModel.getElementAt(i).getDescription();
        if (!filter.add(s)) myDuplicateDescriptions.add(s);
      }
    }

    return myDuplicateDescriptions.contains(description);
  }
}
