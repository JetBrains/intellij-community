// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.configurable;

import com.intellij.application.options.colors.ColorAndFontDescription;
import com.intellij.application.options.colors.ColorAndFontDescriptionPanel;
import com.intellij.application.options.colors.TextAttributesDescription;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

final class PatternDialog extends DialogWrapper {
  private final TodoPattern myPattern;

  private final ComboBox<Icon> myIconComboBox;
  private final JBCheckBox myCaseSensitiveCheckBox;
  private final LanguageTextField myPatternStringField;
  private final ColorAndFontDescriptionPanel myColorAndFontDescriptionPanel;
  private final ColorAndFontDescription myColorAndFontDescription;
  private final JBCheckBox myUsedDefaultColorsCheckBox;
  private final int myPatternIndex;
  private final List<TodoPattern> myExistingPatterns;

  PatternDialog(Component parent, TodoPattern pattern, int patternIndex, List<TodoPattern> existingPatterns) {
    super(parent, true);
    myPatternIndex = patternIndex;
    myExistingPatterns = existingPatterns;
    setTitle(IdeBundle.message("title.add.todo.pattern"));
    setResizable(false);

    final TodoAttributes attrs = pattern.getAttributes();
    myPattern = pattern;
    myIconComboBox = new ComboBox<>(new Icon[]{
      null, AllIcons.General.TodoDefault, AllIcons.General.TodoQuestion, AllIcons.General.TodoImportant
    });
    myIconComboBox.setSelectedItem(attrs.getIcon());
    myIconComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(value);
      label.setText(" ");
    }));
    myCaseSensitiveCheckBox = new JBCheckBox(IdeBundle.message("checkbox.case.sensitive"), pattern.isCaseSensitive());

    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parent));
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName("dummy.regexp");
    Language language = fileType instanceof LanguageFileType languageFileType ? languageFileType.getLanguage() : null;
    myPatternStringField = new LanguageTextField(language, project, pattern.getPatternString());

    // use default colors check box
    myUsedDefaultColorsCheckBox = new JBCheckBox(IdeBundle.message("checkbox.todo.use.default.colors"));
    myUsedDefaultColorsCheckBox.setSelected(!attrs.shouldUseCustomTodoColor());

    myColorAndFontDescriptionPanel = new ColorAndFontDescriptionPanel();

    TextAttributes attributes = myPattern.getAttributes().getCustomizedTextAttributes();
    myColorAndFontDescription = new TextAttributesDescription("null", null, attributes, null, //NON-NLS
                                                              EditorColorsManager.getInstance().getGlobalScheme(), null, null) {
      @Override
      public boolean isErrorStripeEnabled() {
        return true;
      }

      @Override
      public boolean isEditable() {
        return true;
      }
    };
    myColorAndFontDescriptionPanel.reset(myColorAndFontDescription);

    updateCustomColorsPanel();
    myUsedDefaultColorsCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateCustomColorsPanel();
      }
    });
    init();
  }

  private void updateCustomColorsPanel() {
    if (useCustomTodoColor()) {
      // restore controls
      myColorAndFontDescriptionPanel.reset(myColorAndFontDescription);
    }
    else {
      // disable controls
      myColorAndFontDescriptionPanel.resetDefault();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPatternStringField;
  }

  @Override
  protected void doOKAction() {
    myPattern.setPatternString(myPatternStringField.getText().trim());
    myPattern.setCaseSensitive(myCaseSensitiveCheckBox.isSelected());

    final TodoAttributes attrs = myPattern.getAttributes();
    attrs.setIcon((Icon)myIconComboBox.getSelectedItem());
    attrs.setUseCustomTodoColor(useCustomTodoColor(), TodoAttributesUtil.getDefaultColorSchemeTextAttributes());

    if (useCustomTodoColor()) {
      myColorAndFontDescriptionPanel.apply(myColorAndFontDescription, null);
    }
    super.doOKAction();
  }

  @Override
  protected @NotNull List<ValidationInfo> doValidateAll() {
    String patternString = myPatternStringField.getText().trim();
    if (patternString.isEmpty()) {
      return Collections.singletonList(new ValidationInfo(IdeBundle.message("error.pattern.should.be.specified"), myPatternStringField));
    }
    for (int i = 0; i < myExistingPatterns.size(); i++) {
      TodoPattern pattern = myExistingPatterns.get(i);
      if (myPatternIndex != i && patternString.equals(pattern.getPatternString())) {
        return Collections.singletonList(new ValidationInfo(IdeBundle.message("error.same.pattern.already.exists"), myPatternStringField));
      }
    }
    return super.doValidateAll();
  }

  private boolean useCustomTodoColor() {
    return !myUsedDefaultColorsCheckBox.isSelected();
  }

  @Override
  protected JComponent createCenterPanel() {
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(IdeBundle.message("label.todo.pattern"), myPatternStringField)
      .addLabeledComponent(IdeBundle.message("label.todo.icon"), myIconComboBox)
      .addComponent(myCaseSensitiveCheckBox)
      .addComponent(myUsedDefaultColorsCheckBox)
      .addComponent(myColorAndFontDescriptionPanel)
      .getPanel();
  }
}
