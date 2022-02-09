// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.core.CoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.structuralsearch.PatternContext;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Bas Leijdekkers
 */
class FileTypeChooser extends ComboBoxAction implements DumbAware {

  private final List<FileTypeInfo> myFileTypeInfos;
  private FileTypeInfo mySelectedItem;
  private Consumer<? super FileTypeInfo> myConsumer;

  FileTypeChooser() {
    myFileTypeInfos = createFileTypeInfos();
    mySelectedItem = myFileTypeInfos.get(0);
    setSmallVariant(false);
  }

  public void setFileTypeInfoConsumer(@Nullable Consumer<? super FileTypeInfo> consumer) {
    myConsumer = consumer;
  }

  private static List<FileTypeInfo> createFileTypeInfos() {
    final List<LanguageFileType> types = new ArrayList<>();
    for (LanguageFileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        types.add(fileType);
      }
    }
    types.sort((o1, o2) -> o1.getDescription().compareToIgnoreCase(o2.getDescription()));
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

      final List<Language> dialects = new ArrayList<>(language.getDialects());
      dialects.sort(Comparator.comparing(Language::getDisplayName));
      for (Language dialect : dialects) {
        if (profile.isMyLanguage(dialect)) {
          infos.add(new FileTypeInfo(fileType, dialect, null, true));
        }
      }
    }
    return infos;
  }

  public void setSelectedItem(@Nullable LanguageFileType type, @Nullable Language dialect, @Nullable PatternContext context) {
    if (type == null) {
      setSelectedItem(null);
    }
    else {
      for (FileTypeInfo info : myFileTypeInfos) {
        if (info.isEqualTo(type, dialect, context)) {
          setSelectedItem(info);
          return;
        }
      }
    }
  }

  private void setSelectedItem(FileTypeInfo info) {
    mySelectedItem = info;
    if (myConsumer != null) {
      myConsumer.accept(info);
    }
  }

  public FileTypeInfo getSelectedItem() {
    return mySelectedItem;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    if (mySelectedItem == null) {
      presentation.setIcon(AllIcons.FileTypes.Unknown);
      presentation.setText(CoreBundle.message("filetype.unknown.description"));
    }
    else {
      presentation.setIcon(mySelectedItem.getFileType().getIcon());
      presentation.setText(mySelectedItem.getText());
    }
  }

  @Override
  protected @NotNull ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    return new ComboBoxButton(presentation) {
      @Override
      public int getDisplayedMnemonicIndex() {
        return -1;
      }
    };
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    final JPanel panel = new JPanel(new BorderLayout(1, 0));
    final ComboBoxButton button = createComboBoxButton(presentation);
    final String text = SSRBundle.message("search.dialog.file.type.label");
    final JLabel label = new JBLabel(text);
    label.setLabelFor(button);
    button.setMnemonic(TextWithMnemonic.parse(text).getMnemonicCode());

    panel.add(label, BorderLayout.WEST);
    panel.add(button, BorderLayout.CENTER);

    return panel;
  }

  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(JComponent button) {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (FileTypeInfo fileTypeInfo : myFileTypeInfos) {
      group.add(new FileTypeInfoAction(fileTypeInfo));
    }
    return group;
  }

  @Override
  protected Condition<AnAction> getPreselectCondition() {
    return action -> ((FileTypeInfoAction)action).getFileTypeInfo() == mySelectedItem;
  }

  private class FileTypeInfoAction extends DumbAwareAction {

    private final FileTypeInfo myFileTypeInfo;

    FileTypeInfoAction(FileTypeInfo fileTypeInfo) {
      myFileTypeInfo = fileTypeInfo;
      final Presentation presentation = getTemplatePresentation();
      presentation.setIcon(fileTypeInfo.isNested() ? null : fileTypeInfo.getFileType().getIcon());
      presentation.setText(fileTypeInfo.isNested() ? "    " + fileTypeInfo.getText() : fileTypeInfo.getText());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setSelectedItem(myFileTypeInfo);
    }

    FileTypeInfo getFileTypeInfo() {
      return myFileTypeInfo;
    }
  }
}
