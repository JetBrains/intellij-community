// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.create;

import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ObjectUtils;
import org.editorconfig.language.messages.EditorConfigBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreateEditorConfigForm {
  private JPanel     myTopPanel;
  private JBCheckBox myStandardPropertiesCb;
  private JBCheckBox myIntelliJPropertiesCb;
  private JBCheckBox myRootCb;
  private JPanel     myPropertiesPanel;
  private JPanel     myLanguagesPanel;
  private JBCheckBox myCommentProperties;

  private final List<LanguageCheckBoxRec> myLanguageCheckBoxes;

  private final static int MAX_LANGUAGES_ROWS = 10;

  public CreateEditorConfigForm() {
    myPropertiesPanel.setBorder(IdeBorderFactory.createTitledBorder(EditorConfigBundle.message("export.properties.title"), true));
    myLanguagesPanel.setBorder(IdeBorderFactory.createTitledBorder(EditorConfigBundle.message("export.languages.title"), true));
    myLanguagesPanel.setLayout(new BoxLayout(myLanguagesPanel, BoxLayout.X_AXIS));
    myLanguageCheckBoxes = creteLanguageCheckBoxes(myLanguagesPanel);
    setLanguagePanelEnabled(false);
    myCommentProperties.setEnabled(false);
    myIntelliJPropertiesCb.addActionListener(new LanguagePanelEnabler());
    myStandardPropertiesCb.addActionListener(new LanguagePanelEnabler());
  }

  private class LanguagePanelEnabler implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      final boolean addProperties = myIntelliJPropertiesCb.isSelected() || myStandardPropertiesCb.isSelected();
      setLanguagePanelEnabled(addProperties);
      myCommentProperties.setEnabled(addProperties);
    }
  }

  private static List<LanguageCheckBoxRec> creteLanguageCheckBoxes(JPanel languagesPanel) {
    List<LanguageCheckBoxRec> checkBoxes = new ArrayList<>();
    JPanel currPanel = null;
    int rowCount = 0;
    for (Language language : LanguageCodeStyleSettingsProvider.getLanguagesWithCodeStyleSettings()) {
      LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
      if (provider != null && provider.supportsExternalFormats()) {
        if (currPanel == null) {
          currPanel = createLanguageColumnPanel();
          languagesPanel.add(currPanel);
          rowCount = 0;
        }
        String langName = ObjectUtils.notNull(provider.getLanguageName(), language.getDisplayName());
        final JBCheckBox checkBox = new JBCheckBox(langName);
        checkBoxes.add(new LanguageCheckBoxRec(language, checkBox));
        currPanel.add(checkBox);
        rowCount ++;
        if (rowCount >= MAX_LANGUAGES_ROWS) {
          currPanel = null;
        }
      }
    }
    return checkBoxes;
  }

  private void setLanguagePanelEnabled(boolean enabled) {
    myLanguagesPanel.setEnabled(enabled);
    for (LanguageCheckBoxRec checkBoxRec : myLanguageCheckBoxes) {
      checkBoxRec.myCheckBox.setEnabled(enabled);
    }
  }

  private static JPanel createLanguageColumnPanel() {
    JPanel colPanel = new JPanel();
    colPanel.setLayout(new BoxLayout(colPanel, BoxLayout.Y_AXIS));
    colPanel.setAlignmentY(Component.TOP_ALIGNMENT);
    return colPanel;
  }

  public JPanel getTopPanel() {
    return myTopPanel;
  }

  boolean isStandardProperties() {
    return myStandardPropertiesCb.isSelected();
  }

  boolean isIntelliJProperties() {
    return myIntelliJPropertiesCb.isSelected();
  }

  boolean isRoot() {
    return myRootCb.isSelected();
  }

  public List<Language> getSelectedLanguages() {
    return myLanguageCheckBoxes.stream()
      .filter(rec -> rec.myCheckBox.isSelected())
      .map(rec -> rec.myLanguage).collect(Collectors.toList());
  }

  public boolean isCommentProperties() {
    return myCommentProperties.isSelected();
  }

  private static class LanguageCheckBoxRec {
    private final Language  myLanguage;
    private final JCheckBox myCheckBox;

    private LanguageCheckBoxRec(Language language, JCheckBox checkBox) {
      myLanguage = language;
      myCheckBox = checkBox;
    }
  }
}
