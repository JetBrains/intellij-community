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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diff.impl.patch.SelectFilesToAddTextsToPatchPanel;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.SplitterWithSecondHideable;
import com.intellij.util.Consumer;
import com.intellij.util.OnOffListener;
import com.intellij.util.ui.AdjustComponentWhenShown;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CreatePatchConfigurationPanel {
  private static final String SYSTEM_DEFAULT = IdeBundle.message("encoding.name.system.default");

  public static final String ALL = "(All)";
  private JPanel myPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JCheckBox myReversePatchCheckbox;
  private JComboBox myEncoding;
  private JLabel myErrorLabel;
  private JCheckBox myIncludeBaseRevisionTextCheckBox;
  private Consumer<Boolean> myOkEnabledListener;
  private final Project myProject;
  private boolean myDvcsIsUsed;
  private List<Change> myChanges;
  private Collection<Change> myIncludedChanges;
  private SelectFilesToAddTextsToPatchPanel mySelectFilesToAddTextsToPatchPanel;
  private SplitterWithSecondHideable mySplitterWithSecondHideable;

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

    myIncludeBaseRevisionTextCheckBox.setVisible(false);

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
          if (myIncludeBaseRevisionTextCheckBox.isVisible()) {
            // a hack =(
            final JDialog dialog = getParentDialog();
            final Dimension dialogSize = dialog.getSize();
            dialog.setSize(dialogSize.width, dialogSize.height + 1);
            if (dialogSize.width < 500) {
              dialog.setSize(500, dialogSize.height - 1);
            } else {
              dialog.setSize(dialogSize.width, dialogSize.height - 1);
            }
            dialog.repaint();
            /*if (myIncludeBaseRevisionTextCheckBox.isVisible() && VcsConfiguration.getInstance(myProject).CREATE_PATCH_EXPAND_DETAILS_DEFAULT) {
              mySplitterWithSecondHideable.on();
            }*/
          }
        }
        return false;
      }
    }.install(myPanel);
    initEncodingCombo();
  }

  private void initEncodingCombo() {
    final DefaultComboBoxModel encodingsModel = new DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets());
    encodingsModel.insertElementAt(SYSTEM_DEFAULT, 0);
    myEncoding.setModel(encodingsModel);

    final String name = EncodingManager.getInstance().getDefaultCharsetName();
    if (StringUtil.isEmpty(name)) {
      myEncoding.setSelectedItem(SYSTEM_DEFAULT);
    }
    else {
      myEncoding.setSelectedItem(EncodingManager.getInstance().getDefaultCharset());
    }
  }

  @Nullable
  public Charset getEncoding() {
    final Object selectedItem = myEncoding.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(selectedItem)) {
      return EncodingManager.getInstance().getDefaultCharset();
    }
    return (Charset) selectedItem;
  }

  private void initUi() {
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setMinimumSize(new Dimension(400, -1));
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                   new Insets(1, 1, 1, 1), 0, 0);
    gb.anchor = GridBagConstraints.WEST;
    final JLabel createPatchLabel = new JLabel(VcsBundle.message("create.patch.file.path"));
    myPanel.add(createPatchLabel, gb);
    gb.anchor = GridBagConstraints.NORTHWEST;
    myFileNameField = new TextFieldWithBrowseButton();
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

    gb.anchor = GridBagConstraints.WEST;
    myPanel.add(new JLabel("Encoding:"), gb);
    ++ gb.gridx;
    gb.anchor = GridBagConstraints.NORTHWEST;
    myEncoding = new JComboBox();
    myPanel.add(myEncoding, gb);
    ++ gb.gridy;

    gb.gridx = 0;
    myIncludeBaseRevisionTextCheckBox = new JCheckBox("Include base revision text(s) into patch file");
    myPanel.add(myIncludeBaseRevisionTextCheckBox, gb);
    ++ gb.gridy;

    myErrorLabel = new JLabel();
    myPanel.add(myErrorLabel, gb);
  }

  private void initSplitter(Runnable inclusionListener) {
    mySelectFilesToAddTextsToPatchPanel = new SelectFilesToAddTextsToPatchPanel(myProject, myChanges, myIncludedChanges, inclusionListener);
    final Dimension preferredSize = myPanel.getPreferredSize();
    final JPanel wrapper = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        return preferredSize;
      }

      @Override
      public Dimension getMaximumSize() {
        return preferredSize;
      }

      @Override
      public Dimension getMinimumSize() {
        return preferredSize;
      }
    };
    wrapper.add(myPanel, BorderLayout.NORTH);
    mySplitterWithSecondHideable = new SplitterWithSecondHideable(true, ALL, wrapper, new OnOffListener<Integer>() {
      @Override
      public void on(Integer integer) {
        VcsConfiguration.getInstance(myProject).CREATE_PATCH_EXPAND_DETAILS_DEFAULT = true;
        final JDialog dialog = getParentDialog();
        final Dimension dialogSize = dialog.getSize();
        dialog.setSize(dialogSize.width, dialogSize.height + integer);
        dialog.repaint();
      }

      @Override
      public void off(Integer integer) {
        VcsConfiguration.getInstance(myProject).CREATE_PATCH_EXPAND_DETAILS_DEFAULT = false;
        final JDialog dialog = getParentDialog();
        final Dimension dialogSize = dialog.getSize();
        dialog.setSize(dialogSize.width, dialogSize.height - integer);
        dialog.repaint();
      }
    }, false) {
      @Override
      protected RefreshablePanel createDetails() {
        return mySelectFilesToAddTextsToPatchPanel;
      }

      @Override
      protected float getSplitterInitialProportion() {
        return 0.4f;
      }
    };
  }

  private JDialog getParentDialog() {
    Container parent = myPanel.getParent();
    while (! (parent instanceof JDialog)) {
      parent = parent.getParent();
    }
    return (JDialog)parent;
  }

  public void showTextStoreOption(final boolean dvcsIsUsed) {
    myDvcsIsUsed = dvcsIsUsed;
    if (myChanges.size() > 0) {
      myIncludeBaseRevisionTextCheckBox.setVisible(true);

      final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);

      boolean turnOn = dvcsIsUsed || configuration.INCLUDE_TEXT_INTO_PATCH;
      myIncludeBaseRevisionTextCheckBox.setSelected(turnOn);
      myIncludeBaseRevisionTextCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          mySelectFilesToAddTextsToPatchPanel.setEnabled(myIncludeBaseRevisionTextCheckBox.isSelected());
          mySplitterWithSecondHideable.setEnabledColor(myIncludeBaseRevisionTextCheckBox.isSelected());
        }
      });
      final Runnable inclusionListener = new Runnable() {
        @Override
        public void run() {
          if (mySelectFilesToAddTextsToPatchPanel != null) {
            myIncludedChanges = mySelectFilesToAddTextsToPatchPanel.getIncludedChanges();
            mySplitterWithSecondHideable.setText("Selected: " + (myIncludedChanges.size() == myChanges.size() ?
                                       "All" : ("" + myIncludedChanges.size() + " of " + myChanges.size())));
          }
        }
      };
      initSplitter(inclusionListener);
      inclusionListener.run();
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

  public JComponent getPanel() {
    return ! myIncludeBaseRevisionTextCheckBox.isVisible() || myChanges.isEmpty() ? myPanel : mySplitterWithSecondHideable.getComponent();
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
    myIncludedChanges.removeAll(SelectFilesToAddTextsToPatchPanel.getBig(myChanges));
  }
}