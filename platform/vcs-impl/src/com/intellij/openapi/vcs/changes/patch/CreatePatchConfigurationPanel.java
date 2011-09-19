/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.11.2006
 * Time: 19:04:28
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.SelectFilesToAddTextsToPatchDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.Consumer;
import com.intellij.util.ui.AdjustComponentWhenShown;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CreatePatchConfigurationPanel {
  public static final String ALL = "(All)";
  private JPanel myPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JCheckBox myReversePatchCheckbox;
  private JLabel myErrorLabel;
  private JCheckBox myIncludeBaseRevisionTextCheckBox;
  private JLabel myBaseRevisionTextShouldLabel;
  private Consumer<Boolean> myOkEnabledListener;
  private final Project myProject;
  private boolean myDvcsIsUsed;
  private List<Change> myChanges;
  private FixedSizeButton myFixedSizeSelect;
  private JLabel mySelectedLabel;
  private Collection<Change> myIncludedChanges;

  public CreatePatchConfigurationPanel(final Project project) {
    myProject = project;
    initUi();

    myFileNameField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileSaverDialog dialog =
          FileChooserFactory.getInstance().createSaveFileDialog(
            new FileSaverDescriptor("Save patch to", ""), myPanel);
        final String path = FileUtil.toSystemIndependentName(myFileNameField.getText().trim());
        final int idx = path.lastIndexOf("/");
        VirtualFile baseDir = idx == -1 ? project.getBaseDir() :
                              (LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path.substring(0, idx))));
        baseDir = baseDir == null ? project.getBaseDir() : baseDir;
        final String name = idx == -1 ? path : path.substring(idx + 1);
        final VirtualFileWrapper fileWrapper = dialog.save(baseDir, name);
        if (fileWrapper != null) {
          myFileNameField.setText(fileWrapper.getFile().getPath());
          checkName();
        }
      }
    });

    //myBaseRevisionTextShouldLabel.setUI(new MultiLineLabelUI());
    myBaseRevisionTextShouldLabel.setForeground(UIUtil.getInactiveTextColor());
    myBaseRevisionTextShouldLabel.setVisible(false);
    myIncludeBaseRevisionTextCheckBox.setVisible(false);
    myFixedSizeSelect.setVisible(false);
    mySelectedLabel.setVisible(false);

    myFileNameField.getTextField().addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(final InputMethodEvent event) {
        checkName();
      }

      public void caretPositionChanged(final InputMethodEvent event) {
      }
    });
    myFileNameField.getTextField().addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        checkName();
      }

      public void keyPressed(final KeyEvent e) {
        checkName();
      }

      public void keyReleased(final KeyEvent e) {
        checkName();
      }
    });
    myErrorLabel.setForeground(Color.RED);
    checkName();
    new AdjustComponentWhenShown() {
      @Override
      protected boolean init() {
        if (myPanel.isVisible()) {
          IdeFocusManager.findInstanceByComponent(myPanel).requestFocus(myFileNameField.getTextField(), true);
        }
        return false;
      }
    }.install(myPanel);
  }

  private void initUi() {
    myPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                   new Insets(1, 1, 1, 1), 0, 0);
    gb.anchor = GridBagConstraints.WEST;
    final JLabel createPatchLabel = new JLabel(VcsBundle.message("create.patch.file.path"));
    myPanel.add(createPatchLabel, gb);
    gb.anchor = GridBagConstraints.NORTHWEST;
    myFileNameField = new TextFieldWithBrowseButton();
    new AdjustComponentWhenShown() {
      @Override
      protected boolean init() {
        /*if (myFileNameField.getHeight() == 0) return false;
        myFileNameField.getTextField().setMinimumSize(new Dimension(30, myFileNameField.getHeight()));*/
        if (myChanges.size() > 0 && myIncludeBaseRevisionTextCheckBox.isSelected()) {
          myFixedSizeSelect.doClick();
        }
        return true;
      }
    }.install(myPanel);
    ++ gb.gridx;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;
    myPanel.add(myFileNameField, gb);

    /*gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;*/
    gb.gridx = 0;
    ++ gb.gridy;
    gb.gridwidth = 2;
    myReversePatchCheckbox = new JCheckBox(VcsBundle.message("create.patch.reverse.patch.checkbox"));
    ++ gb.gridy;
    myPanel.add(myReversePatchCheckbox, gb);
    ++ gb.gridy;
    gb.gridwidth = 2;
    
    final JPanel wrapper = new JPanel(new BorderLayout());
    myIncludeBaseRevisionTextCheckBox = new JCheckBox("Include base revision text(s) into patch file");
    wrapper.add(myIncludeBaseRevisionTextCheckBox, BorderLayout.WEST);
    final JPanel wr2 = new JPanel(new BorderLayout());
    wrapper.add(wr2, BorderLayout.EAST);
    myFixedSizeSelect = new FixedSizeButton(myIncludeBaseRevisionTextCheckBox);
    wr2.add(myFixedSizeSelect, BorderLayout.WEST);
    mySelectedLabel = new JLabel(ALL);
    mySelectedLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    mySelectedLabel.setFont(mySelectedLabel.getFont().deriveFont(Font.BOLD));
    wr2.add(mySelectedLabel, BorderLayout.EAST);
    gb.fill = GridBagConstraints.NONE;
    gb.weightx = 0;
    myPanel.add(wrapper, gb);
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;

    myIncludeBaseRevisionTextCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySelectedLabel.setEnabled(myIncludeBaseRevisionTextCheckBox.isSelected());
        myFixedSizeSelect.setEnabled(myIncludeBaseRevisionTextCheckBox.isSelected());
        myFixedSizeSelect.doClick();
      }
    });
    myFixedSizeSelect.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final SelectFilesToAddTextsToPatchDialog dialog = new SelectFilesToAddTextsToPatchDialog(myProject, myChanges, myIncludedChanges);
        dialog.show();
        if (dialog.isOK()) {
          myIncludedChanges = dialog.getIncludedChanges();
          recalculateSelectedLabel();
        }
      }
    });

    gb.gridx = 0;
    gb.gridwidth = 2;
    ++ gb.gridy;
    final StringBuilder sb = new StringBuilder().append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())).
      append("</head><body>Base revision text should be included into patch, if it is about to be used in projects under DVCS.").
      append("<br/>In DVCS commits can be reordered, so there could exist no revision any more with the text matching patch context.<br/><br/>").
      append("Only modified files texts needs to be added. Add/delete changes are self-descriptive.").
      append("</body></html>");

    myBaseRevisionTextShouldLabel = new JLabel(sb.toString());
    myPanel.add(myBaseRevisionTextShouldLabel, gb);

    ++ gb.gridy;
    myErrorLabel = new JLabel();
    myPanel.add(myErrorLabel, gb);
  }

  private void recalculateSelectedLabel() {
    mySelectedLabel.setText("(Selected " + myIncludedChanges.size() + " of " + myChanges.size() + " modified)");
  }

  public void showTextStoreOption(final boolean dvcsIsUsed) {
    myDvcsIsUsed = dvcsIsUsed;
    if (myChanges.size() > 0) {
      myBaseRevisionTextShouldLabel.setVisible(true);
      myIncludeBaseRevisionTextCheckBox.setVisible(true);
      myFixedSizeSelect.setVisible(true);
      mySelectedLabel.setVisible(true);

      final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);

      boolean turnOn = dvcsIsUsed || configuration.INCLUDE_TEXT_INTO_PATCH;
      myIncludeBaseRevisionTextCheckBox.setSelected(turnOn);
    }
  }

  private void checkName() {
    final PatchNameChecker patchNameChecker = new PatchNameChecker(myFileNameField.getText());
    if (patchNameChecker.nameOk()) {
      myErrorLabel.setText("");
    } else {
      myErrorLabel.setText(patchNameChecker.getError());
    }
    if (myOkEnabledListener != null) {
      myOkEnabledListener.consume(patchNameChecker.nameOk());
    }
  }

  public void onOk() {
    if (myIncludeBaseRevisionTextCheckBox.isVisible() && ! myDvcsIsUsed) {
      final VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
      vcsConfiguration.INCLUDE_TEXT_INTO_PATCH = myIncludeBaseRevisionTextCheckBox.isSelected();
    }
  }

  public boolean isStoreTexts() {
    return myIncludeBaseRevisionTextCheckBox.isSelected();
  }

  public Collection<Change> getIncludedChanges() {
    return myIncludedChanges;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void installOkEnabledListener(final Consumer<Boolean> runnable) {
    myOkEnabledListener = runnable;
  }

  public String getFileName() {
    return myFileNameField.getText();
  }

  public void setFileName(final File file) {
    myFileNameField.setText(file.getPath());
    checkName();
  }

  public boolean isReversePatch() {
    return myReversePatchCheckbox.isSelected();
  }

  public void setReversePatch(boolean reverse) {
    myReversePatchCheckbox.setSelected(reverse);
  }

  public boolean isOkToExecute() {
    return myErrorLabel.getText() == null || myErrorLabel.getText().length() == 0;
  }

  public String getError() {
    return myErrorLabel.getText() == null ? "" : myErrorLabel.getText();
  }

  public void setChanges(Collection<Change> changes) {
    myChanges = new ArrayList<Change>(changes);
    myIncludedChanges = new ArrayList<Change>(myChanges);
    myIncludedChanges.removeAll(SelectFilesToAddTextsToPatchDialog.getBig(myChanges));
    recalculateSelectedLabel();
  }
}