// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.structuralsearch.PatternContext;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public class FileTypeSelector extends ComboBox<FileTypeInfo> {

  public FileTypeSelector() {
    super(createModel());
    setRenderer(new MyCellRenderer());
    setSwingPopup(false);
  }

  @Nullable
  @Override
  public FileTypeInfo getSelectedItem() {
    return (FileTypeInfo)super.getSelectedItem();
  }

  @Nullable
  public LanguageFileType getSelectedFileType() {
    final FileTypeInfo info = (FileTypeInfo)super.getSelectedItem();
    return info != null ? info.getFileType() : null;
  }

  public void setSelectedItem(@NotNull LanguageFileType type,
                              @Nullable Language dialect,
                              @Nullable PatternContext context) {
    final DefaultComboBoxModel<FileTypeInfo> model = (DefaultComboBoxModel<FileTypeInfo>)getModel();
    for (int i = 0; i < model.getSize(); i++) {
      final FileTypeInfo info = model.getElementAt(i);
      if (info.isEqualTo(type, dialect, context)) {
        setSelectedItem(info);
        return;
      }
    }
  }

  @NotNull
  private static DefaultComboBoxModel<FileTypeInfo> createModel() {
    final List<LanguageFileType> types = new ArrayList<>();
    for (LanguageFileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        types.add(fileType);
      }
    }
    Collections.sort(types, (o1, o2) -> o1.getDescription().compareToIgnoreCase(o2.getDescription()));
    final List<FileTypeInfo> infos = new ArrayList<>();
    for (LanguageFileType fileType : types) {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
      assert profile != null;
      final Language language = fileType.getLanguage();
      final List<PatternContext> patternContexts = new ArrayList<>(profile.getPatternContexts());
      if (!patternContexts.isEmpty()) {
        infos.add(new FileTypeInfo(fileType, language, patternContexts.get(0), false));
        for (int i = 1; i < patternContexts.size(); i++) {
          infos.add(new FileTypeInfo(fileType, language, patternContexts.get(i), true));
        }
        continue; // proceed with the next file type
      }

      infos.add(new FileTypeInfo(fileType, language, null, false));

      final Language[] languageDialects = LanguageUtil.getLanguageDialects(language);
      Arrays.sort(languageDialects, Comparator.comparing(Language::getDisplayName));
      for (Language dialect : languageDialects) {
        if (profile.isMyLanguage(dialect)) {
          infos.add(new FileTypeInfo(fileType, dialect, null, true));
        }
      }
    }

    return new MyComboBoxModel(infos);
  }

  private static class MyComboBoxModel extends DefaultComboBoxModel<FileTypeInfo> {
    MyComboBoxModel(List<FileTypeInfo> infos) {
      super(infos.toArray(FileTypeInfo.EMPTY_ARRAY));
    }
  }

  private static class MyCellRenderer extends SimpleListCellRenderer<FileTypeInfo> {
    private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;
    private static final Icon WIDE_EMPTY_ICON = JBUI.scale(EmptyIcon.create(32, 18));

    MyCellRenderer() {}

    @Override
    public void customize(JList<? extends FileTypeInfo> list, FileTypeInfo value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        return;
      }
      setIcon(value.isNested() && index >= 0 ? WIDE_EMPTY_ICON : getFileTypeIcon(value));
      setText(value.getText());
    }

    @NotNull
    private static Icon getFileTypeIcon(FileTypeInfo info) {
      final LayeredIcon layeredIcon = new LayeredIcon(2);
      layeredIcon.setIcon(EMPTY_ICON, 0);
      final Icon icon = info.getFileType().getIcon();
      if (icon != null) {
        layeredIcon.setIcon(icon, 1);
      }
      return layeredIcon;
    }
  }
}
