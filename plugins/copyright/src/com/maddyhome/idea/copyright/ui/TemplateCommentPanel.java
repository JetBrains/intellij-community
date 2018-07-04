/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.UIUtil;
import com.maddyhome.idea.copyright.CopyrightProfileKt;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.pattern.EntityUtil;
import com.maddyhome.idea.copyright.pattern.VelocityHelper;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TemplateCommentPanel implements SearchableConfigurable {
  private final CopyrightManager myManager;

  private final FileType fileType;
  private final TemplateCommentPanel parentPanel;
  private JRadioButton[] fileLocations = null;

  private JTextArea preview;
  private JPanel mainPanel;

  private JRadioButton rbBefore;
  private JRadioButton rbAfter;

  private JPanel fileLocationPanel;

  private JRadioButton myUseDefaultSettingsRadioButton;
  private JRadioButton myUseCustomFormattingOptionsRadioButton;
  private JRadioButton myNoCopyright;

  private JRadioButton rbLineComment;
  private JCheckBox cbPrefixLines;
  private JRadioButton rbBlockComment;
  private JPanel myCommentTypePanel;

  private JCheckBox cbSeparatorBefore;
  private JTextField txtLengthBefore;
  private JTextField txtLengthAfter;
  private JCheckBox cbAddBlank;
  private JCheckBox cbSeparatorAfter;
  private JCheckBox cbBox;
  private JTextField txtFiller;
  private JPanel myBorderPanel;
  private JLabel lblLengthBefore;
  private JLabel lblLengthAfter;
  private JLabel mySeparatorCharLabel;


  private void updateBox() {
    boolean enable = true;
    if (!cbSeparatorBefore.isSelected() || !cbSeparatorAfter.isSelected()) {
      enable = false;
    }
    else {
      if (!txtLengthBefore.getText().equals(txtLengthAfter.getText())) {
        enable = false;
      }
    }

    boolean either = cbSeparatorBefore.isSelected() || cbSeparatorAfter.isSelected();

    cbBox.setEnabled(enable);

    txtFiller.setEnabled(either);
  }

  private final EventListenerList listeners = new EventListenerList();
  private final boolean allowBlock;


  public TemplateCommentPanel(FileType fileType, TemplateCommentPanel parentPanel, String[] locations, Project project) {
    this.parentPanel = parentPanel;

    myManager = CopyrightManager.getInstance(project);
    if (fileType == null) {
      myUseDefaultSettingsRadioButton.setVisible(false);
      myUseCustomFormattingOptionsRadioButton.setVisible(false);
      myNoCopyright.setVisible(false);
    }

    this.fileType = fileType != null ? fileType : StdFileTypes.JAVA;
    allowBlock = FileTypeUtil.hasBlockComment(this.fileType);

    if (parentPanel != null) {
      parentPanel.addOptionChangeListener(new TemplateOptionsPanelListener() {
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


    addOptionChangeListener(new TemplateOptionsPanelListener() {
      public void optionChanged() {
        showPreview(getOptions());
      }
    });


    preview.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));

    myUseDefaultSettingsRadioButton.setSelected(true);

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateOverride();
      }
    };

    myUseDefaultSettingsRadioButton.addActionListener(listener);
    myUseCustomFormattingOptionsRadioButton.addActionListener(listener);
    myNoCopyright.addActionListener(listener);
    txtLengthBefore.setText("80");
    txtLengthAfter.setText("80");

    rbBlockComment.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        cbPrefixLines.setEnabled(rbBlockComment.isSelected());
        fireChangeEvent();
      }
    });

    rbLineComment.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        cbPrefixLines.setEnabled(rbBlockComment.isSelected());
        fireChangeEvent();
      }
    });

    cbPrefixLines.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        fireChangeEvent();
      }
    });

    cbSeparatorBefore.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        lblLengthBefore.setEnabled(cbSeparatorBefore.isSelected());
        txtLengthBefore.setEnabled(cbSeparatorBefore.isSelected());
        fireChangeEvent();
        updateBox();
      }
    });

    cbSeparatorAfter.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        lblLengthAfter.setEnabled(cbSeparatorAfter.isSelected());
        txtLengthAfter.setEnabled(cbSeparatorAfter.isSelected());
        fireChangeEvent();
        updateBox();
      }
    });

    final DocumentAdapter documentAdapter = new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        fireChangeEvent();
        updateBox();
      }
    };
    txtLengthBefore.getDocument().addDocumentListener(documentAdapter);
    txtLengthAfter.getDocument().addDocumentListener(documentAdapter);
    txtFiller.getDocument().addDocumentListener(documentAdapter);

    cbBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        fireChangeEvent();
      }
    });
  }


  public LanguageOptions getOptions() {
    // If this is a fully custom comment we should really ensure there are no blank lines in the comments outside
    // of a block comment. If there are any blank lines the replacement logic will fall apart.
    final LanguageOptions res = new LanguageOptions();
    res.setBlock(rbBlockComment.isSelected());
    res.setPrefixLines(!allowBlock || cbPrefixLines.isSelected());
    res.setSeparateAfter(cbSeparatorAfter.isSelected());
    res.setSeparateBefore(cbSeparatorBefore.isSelected());
    try {
      res.setLenBefore(Integer.parseInt(txtLengthBefore.getText()));
      res.setLenAfter(Integer.parseInt(txtLengthAfter.getText()));
    }
    catch (NumberFormatException e) {
      //leave blank
    }
    res.setBox(cbBox.isSelected());

    String filler = txtFiller.getText();
    if (filler.length() > 0) {
      res.setFiller(filler);
    }
    else {
      res.setFiller(LanguageOptions.DEFAULT_FILLER);
    }

    res.setFileTypeOverride(getOverrideChoice());
    res.setRelativeBefore(rbBefore.isSelected());
    res.setAddBlankAfter(cbAddBlank.isSelected());
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
    return myUseDefaultSettingsRadioButton.isSelected()
           ? LanguageOptions.USE_TEMPLATE
           : myNoCopyright.isSelected() ? LanguageOptions.NO_COPYRIGHT : LanguageOptions.USE_TEXT;
  }

  private void updateOverride() {
    int choice = getOverrideChoice();
    LanguageOptions parentOpts = parentPanel != null ? parentPanel.getOptions() : null;
    switch (choice) {
      case LanguageOptions.NO_COPYRIGHT:
        enableFormattingOptions(false);
        showPreview(getOptions());
        rbBefore.setEnabled(false);
        rbAfter.setEnabled(false);
        cbAddBlank.setEnabled(false);
        if (fileLocations != null) {
          for (JRadioButton fileLocation : fileLocations) {
            fileLocation.setEnabled(false);
          }
        }
        break;
      case LanguageOptions.USE_TEMPLATE:
        final boolean isTemplate = parentPanel == null;
        enableFormattingOptions(isTemplate);
        showPreview(parentOpts != null ? parentOpts : getOptions());
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
        enableFormattingOptions(true);
        showPreview(getOptions());
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

  private void enableFormattingOptions(boolean enable) {
    if (enable) {
      rbBlockComment.setEnabled(true);
      rbLineComment.setEnabled(true);
      cbPrefixLines.setEnabled(allowBlock);
      cbSeparatorBefore.setEnabled(true);
      cbSeparatorAfter.setEnabled(true);
      lblLengthBefore.setEnabled(cbSeparatorBefore.isSelected());
      txtLengthBefore.setEnabled(cbSeparatorBefore.isSelected());
      lblLengthAfter.setEnabled(cbSeparatorAfter.isSelected());
      txtLengthAfter.setEnabled(cbSeparatorAfter.isSelected());
      mySeparatorCharLabel.setEnabled(true);
      updateBox();
    } else {
      UIUtil.setEnabled(myCommentTypePanel, false, true);
      UIUtil.setEnabled(myBorderPanel, false, true);
    }
  }

  private void showPreview(LanguageOptions options) {
    final String defaultCopyrightText = myNoCopyright.isSelected() ? "" : FileTypeUtil
      .buildComment(fileType, VelocityHelper.evaluate(null, null, null, EntityUtil.decode(CopyrightProfileKt.DEFAULT_COPYRIGHT_NOTICE)), options);
    SwingUtilities.invokeLater(() -> preview.setText(defaultCopyrightText));
  }


  public FileType getFileType() {
    return fileType;
  }

  @Nls
  public String getDisplayName() {
    return fileType instanceof LanguageFileType 
           ? ((LanguageFileType)fileType).getLanguage().getDisplayName() 
           : fileType.getName();
  }

  @Override
  public String getHelpTopic() {
    return "copyright.filetypes";
  }

  public JComponent createComponent() {
    return mainPanel;
  }

  public boolean isModified() {
    if (parentPanel == null) return !myManager.getOptions().getTemplateOptions().equals(getOptions());
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
    final LanguageOptions options =
      parentPanel == null ? myManager.getOptions().getTemplateOptions() : myManager.getOptions().getOptions(fileType.getName());
    boolean isBlock = options.isBlock();
    if (isBlock) {
      rbBlockComment.setSelected(true);
    }
    else {
      rbLineComment.setSelected(true);
    }

    cbPrefixLines.setSelected(!allowBlock || options.isPrefixLines());
    cbSeparatorAfter.setSelected(options.isSeparateAfter());
    cbSeparatorBefore.setSelected(options.isSeparateBefore());
    txtLengthBefore.setText(String.valueOf(options.getLenBefore()));
    txtLengthAfter.setText(String.valueOf(options.getLenAfter()));
    txtFiller.setText(options.getFiller() == LanguageOptions.DEFAULT_FILLER ? "" : options.getFiller());
    cbBox.setSelected(options.isBox());

    final int fileTypeOverride = options.getFileTypeOverride();
    myUseDefaultSettingsRadioButton.setSelected(fileTypeOverride == LanguageOptions.USE_TEMPLATE);
    myUseCustomFormattingOptionsRadioButton.setSelected(fileTypeOverride == LanguageOptions.USE_TEXT);
    myNoCopyright.setSelected(fileTypeOverride == LanguageOptions.NO_COPYRIGHT);
    if (options.isRelativeBefore()) {
      rbBefore.setSelected(true);
    }
    else {
      rbAfter.setSelected(true);
    }
    cbAddBlank.setSelected(options.isAddBlankAfter());

    if (fileLocations != null) {
      int choice = options.getFileLocation() - 1;
      choice = Math.max(0, Math.min(choice, fileLocations.length - 1));
      fileLocations[choice].setSelected(true);
    }

    updateOverride();
  }

  public void addOptionChangeListener(TemplateOptionsPanelListener listener) {
    listeners.add(TemplateOptionsPanelListener.class, listener);
  }

  private void fireChangeEvent() {
    Object[] fires = listeners.getListenerList();
    for (int i = fires.length - 2; i >= 0; i -= 2) {
      if (fires[i] == TemplateOptionsPanelListener.class) {
        ((TemplateOptionsPanelListener)fires[i + 1]).optionChanged();
      }
    }
  }

  @NotNull
  public String getId() {
    return getHelpTopic() + "." + fileType.getName();
  }
}
