// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class FileTypeRenderer extends ListCellRendererWrapper<FileType> {
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;
  private static final Pattern CLEANUP = Pattern.compile("(?i)\\s+file(?:s)?$");

  public interface FileTypeListProvider {
    Iterable<FileType> getCurrentFileTypeList();
  }

  private final FileTypeListProvider myFileTypeListProvider;

  public FileTypeRenderer() {
    this(new DefaultFileTypeListProvider());
  }

  public FileTypeRenderer(@NotNull FileTypeListProvider fileTypeListProvider) {
    super();
    myFileTypeListProvider = fileTypeListProvider;
  }

  @Override
  public void customize(JList list, FileType type, int index, boolean selected, boolean hasFocus) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    final Icon icon = type.getIcon();
    if (icon != null) {
      layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
    }

    setIcon(layeredIcon);

    String description = type.getDescription();
    String trimmedDescription = StringUtil.capitalizeWords(CLEANUP.matcher(description).replaceAll(""), true);
    if (isDuplicated(description)) {
      setText(trimmedDescription + " (" + type.getName() + ")");

    }
    else {
      setText(trimmedDescription);
    }
  }

  private boolean isDuplicated(final String description) {
    boolean found = false;

    for (FileType type : myFileTypeListProvider.getCurrentFileTypeList()) {
      if (description.equals(type.getDescription())) {
        if (!found) {
          found = true;
        }
        else {
          return true;
        }
      }
    }
    return false;
  }

  private static class DefaultFileTypeListProvider implements FileTypeListProvider {
    private final List<FileType> myFileTypes;

    public DefaultFileTypeListProvider() {
      myFileTypes = Arrays.asList(FileTypeManager.getInstance().getRegisteredFileTypes());
    }

    @Override
    public Iterable<FileType> getCurrentFileTypeList() {
      return myFileTypes;
    }
  }
}
