// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class FileTypeSelector extends ComboBox<FileTypeInfo> {

  public FileTypeSelector(@NotNull List<FileType> types) {
    super(createModel(types));
    setRenderer(new MyCellRenderer());
    new MySpeedSearch(this);
  }

  @Nullable
  @Override
  public FileTypeInfo getSelectedItem() {
    return (FileTypeInfo)super.getSelectedItem();
  }

  @Nullable
  public FileType getSelectedFileType() {
    FileTypeInfo info = (FileTypeInfo)super.getSelectedItem();
    return info != null ? info.getFileType() : null;
  }

  public void setSelectedItem(@NotNull FileType type, @Nullable Language dialect) {
    DefaultComboBoxModel<FileTypeInfo> model = (DefaultComboBoxModel<FileTypeInfo>)getModel();
    for (int i = 0; i < model.getSize(); i++) {
      FileTypeInfo info = model.getElementAt(i);
      if (info.getFileType() == type && info.getDialect() == dialect) {
        setSelectedItem(info);
        return;
      }
    }
  }

  @NotNull
  private static DefaultComboBoxModel<FileTypeInfo> createModel(List<FileType> types) {
    final List<FileTypeInfo> infos = new ArrayList<>();
    for (FileType fileType : types) {
      boolean duplicated = isDuplicated(fileType, types);
      infos.add(new FileTypeInfo(fileType, null, duplicated));

      if (fileType instanceof LanguageFileType) {
        final Language language = ((LanguageFileType)fileType).getLanguage();
        final Language[] languageDialects = LanguageUtil.getLanguageDialects(language);
        Arrays.sort(languageDialects, Comparator.comparing(Language::getDisplayName));
        for (Language dialect : languageDialects) {
          infos.add(new FileTypeInfo(fileType, dialect, duplicated));
        }
      }
    }

    return new DefaultComboBoxModel<>(infos.toArray(FileTypeInfo.EMPTY_ARRAY));
  }

  private static boolean isDuplicated(@NotNull FileType fileType, @NotNull List<FileType> types) {
    String description = fileType.getDescription();
    for (FileType type : types) {
      if (type != fileType && description.equals(type.getDescription())) {
        return true;
      }
    }
    return false;
  }

  private static class MyCellRenderer extends ListCellRendererWrapper<FileTypeInfo> {
    private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;
    private static final Icon WIDE_EMPTY_ICON = JBUI.scale(EmptyIcon.create(32, 18));

    @Override
    public void customize(JList list, FileTypeInfo info, int index, boolean selected, boolean hasFocus) {
      if (info.isDialect() && index >= 0) {
        setIcon(WIDE_EMPTY_ICON);
        setText(info.getText());
      }
      else {
        setIcon(getFileTypeIcon(info));
        setText(info.getFullText());
      }
    }

    @NotNull
    private static Icon getFileTypeIcon(FileTypeInfo info) {
      LayeredIcon layeredIcon = new LayeredIcon(2);
      layeredIcon.setIcon(EMPTY_ICON, 0);
      Icon icon = info.getFileType().getIcon();
      if (icon != null) {
        layeredIcon.setIcon(icon, 1);
      }
      return layeredIcon;
    }
  }

  private static class MySpeedSearch extends ComboboxSpeedSearch {
    public MySpeedSearch(FileTypeSelector comboBox) {super(comboBox);}

    @Override
    protected String getElementText(Object element) {
      return ((FileTypeInfo)element).getText();
    }
  }
}
