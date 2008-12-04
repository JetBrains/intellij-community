/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.options.TemplateOptions;
import com.maddyhome.idea.copyright.pattern.EntityUtil;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class TemplateCommentPanel implements Configurable {
  private CopyrightManager myManager;

  private FileType fileType;
  private TemplateCommentPanel parentPanel;
  private JRadioButton[] fileLocations = null;


  private TemplateOptionsPanel tempOptionsPanel;
  private JTextArea preview;
  private JPanel mainPanel;

  private JRadioButton rbBefore;
  private JRadioButton rbAfter;
  private JPanel fileLocationPanel;
  private JCheckBox cbAddBlank;
  private JCheckBox cbUseAlternate;
  private JRadioButton myUseDefaultSettingsRadioButton;
  private JRadioButton myUseCustomFormattingOptionsRadioButton;

  public TemplateCommentPanel(FileType fileType, TemplateCommentPanel parentPanel, String[] locations, Project project) {
    this.parentPanel = parentPanel;

    myManager = CopyrightManager.getInstance(project);
    if (fileType == null) {
      myUseDefaultSettingsRadioButton.setVisible(false);
      myUseCustomFormattingOptionsRadioButton.setVisible(false);
    }

    this.fileType = fileType != null ? fileType : StdFileTypes.JAVA;
    tempOptionsPanel.setFileType(this.fileType);
    FileType alternate = FileTypeUtil.getInstance().getAlternate(this.fileType);
    if (alternate != null) {
      cbUseAlternate.setText("Use " + alternate.getName() + " Comments");
      cbUseAlternate.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateOverride();
        }
      });
    }
    else {
      cbUseAlternate.setVisible(false);
    }

    if (parentPanel != null) {
      parentPanel.tempOptionsPanel.addOptionChangeListener(new TemplateOptionsPanelListener() {
        public void optionChanged() {
          updateOverride();
        }
      });
    }

    ButtonGroup group = new ButtonGroup();
    group.add(rbBefore);
    group.add(rbAfter);

    if (locations == null) {
      fileLocationPanel.setBorder(BorderFactory.createEmptyBorder());
    }
    else {
      fileLocations = new JRadioButton[locations.length];
      group = new ButtonGroup();
      for (int i = 0; i < fileLocations.length; i++) {
        fileLocations[i] = new JRadioButton(locations[i]);
        group.add(fileLocations[i]);

        fileLocationPanel.add(fileLocations[i], new GridConstraints(i, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null));
      }
    }




    tempOptionsPanel.addOptionChangeListener(new TemplateOptionsPanelListener() {
      public void optionChanged() {
        showPreview(tempOptionsPanel.getOptions());
      }
    });


    preview.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));

    preview.setColumns(CodeStyleSettingsManager.getInstance().getCurrentSettings().RIGHT_MARGIN);

    myUseDefaultSettingsRadioButton.setSelected(true);

        ItemListener listener = new ItemListener()
        {
            public void itemStateChanged(ItemEvent itemEvent)
            {
                if (itemEvent.getStateChange() == ItemEvent.SELECTED)
                {
                    updateOverride();
                }
            }
        };

        myUseDefaultSettingsRadioButton.addItemListener(listener);
        myUseCustomFormattingOptionsRadioButton.addItemListener(listener);

  }


  public LanguageOptions getOptions() {
    // If this is a fully custom comment we should really ensure there are no blank lines in the comments outside
    // of a block comment. If there are any blank lines the replacement logic will fall apart.
    LanguageOptions res = new LanguageOptions();
    res.setTemplateOptions(tempOptionsPanel.getOptions());

    res.setFileTypeOverride(getOverrideChoice());
    res.setRelativeBefore(rbBefore.isSelected());
    res.setAddBlankAfter(cbAddBlank.isSelected());
    res.setUseAlternate(cbUseAlternate.isSelected());
    if (fileLocations != null) {
      for (int i = 0; i < fileLocations.length; i++) {
        if (fileLocations[i].isSelected()) {
          res.setFileLocation(i + 1);
        }
      }
    }

    return res;
  }

  private int getOverrideChoice() {
    return myUseDefaultSettingsRadioButton.isSelected() ? LanguageOptions.USE_TEMPLATE : LanguageOptions.USE_TEXT;
  }

  private void updateOverride() {
    int choice = getOverrideChoice();
    LanguageOptions parentOpts = parentPanel != null ? parentPanel.getOptions() : null;
    switch (choice) {
      case LanguageOptions.USE_TEMPLATE:
        final boolean isTemplate = parentPanel == null;
        tempOptionsPanel.setEnabled(isTemplate);
        showPreview(parentOpts != null ? parentOpts.getTemplateOptions() : getOptions().getTemplateOptions());

        rbBefore.setEnabled(isTemplate);
        rbAfter.setEnabled(isTemplate);
        cbAddBlank.setEnabled(isTemplate);
        if (fileLocations != null) {
          for (JRadioButton fileLocation : fileLocations) {
            fileLocation.setEnabled(true);
          }
        }
        break;
      case LanguageOptions.USE_TEXT:
        tempOptionsPanel.setEnabled(true);
        showPreview(tempOptionsPanel.getOptions());
        rbBefore.setEnabled(true);
        rbAfter.setEnabled(true);
        cbAddBlank.setEnabled(true);
        if (fileLocations != null) {
          for (JRadioButton fileLocation : fileLocations) {
            fileLocation.setEnabled(true);
          }
        }
        break;
    }
  }

  private void showPreview(TemplateOptions options) {
    final String defaultCopyrightText =
      FileTypeUtil.buildComment(fileType, cbUseAlternate.isSelected(), EntityUtil.decode(CopyrightProfile.DEFAULT_COPYRIGHT_NOTICE), options);
    preview.setText(defaultCopyrightText);
  }


  public FileType getFileType() {
    return fileType;
  }

  @Nls
  public String getDisplayName() { //todo mapped names
    return fileType.getName();
  }

  public Icon getIcon() {
    return fileType.getIcon();
  }

  public String getHelpTopic() {
    return "copyright.filetypes";
  }

  public JComponent createComponent() {
    return mainPanel;
  }

  public boolean isModified() {
    return !myManager.getOptions().getOptions(fileType.getName()).equals(getOptions());
  }

  public void apply() throws ConfigurationException {
    final Options options = myManager.getOptions();
    if (parentPanel == null) {
      options.setTemplateOptions(getOptions());
    }
    else {
      options.setOptions(fileType.getName(), getOptions());
    }
  }

  public void reset() {
    LanguageOptions options = myManager.getOptions().getOptions(fileType.getName());

    tempOptionsPanel
      .setOptions(parentPanel == null ? myManager.getOptions().getTemplateOptions().getTemplateOptions() : options.getTemplateOptions());
    final boolean isTemplate = options.getFileTypeOverride() == LanguageOptions.USE_TEMPLATE;
    myUseDefaultSettingsRadioButton.setSelected(isTemplate);
    myUseCustomFormattingOptionsRadioButton.setSelected(!isTemplate);
    if (options.isRelativeBefore()) {
      rbBefore.setSelected(true);
    }
    else {
      rbAfter.setSelected(true);
    }
    cbAddBlank.setSelected(options.isAddBlankAfter());
    cbUseAlternate.setSelected(options.isUseAlternate());

    if (fileLocations != null) {
      int choice = options.getFileLocation() - 1;
      choice = Math.max(0, Math.min(choice, fileLocations.length - 1));
      fileLocations[choice].setSelected(true);
    }

    updateOverride();
  }

  public void disposeUIResources() {
  }
}