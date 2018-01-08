/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.readOnlyHandler.FileListRenderer;
import com.intellij.openapi.vcs.readOnlyHandler.ReadOnlyStatusDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public class NonProjectFileWritingAccessDialog extends DialogWrapper {
  private JPanel myPanel;
  private JLabel myListTitle;
  private JList myFileList;
  private JRadioButton myUnlockOneButton;
  private JRadioButton myUnlockDirButton;
  private JRadioButton myUnlockAllButton;

  public NonProjectFileWritingAccessDialog(@NotNull Project project, @NotNull List<VirtualFile> nonProjectFiles) {
    this(project, nonProjectFiles, "Non-Project Files");
  }

  public NonProjectFileWritingAccessDialog(@NotNull Project project,
                                           @NotNull List<VirtualFile> nonProjectFiles,
                                           @NotNull String filesType) {
    super(project);
    setTitle(filesType + " Protection");

    myFileList.setPreferredSize(ReadOnlyStatusDialog.getDialogPreferredSize());
    
    myFileList.setCellRenderer(new FileListRenderer());
    myFileList.setModel(new CollectionListModel<>(nonProjectFiles));

    String theseFilesMessage = ReadOnlyStatusDialog.getTheseFilesMessage(nonProjectFiles);
    myListTitle.setText(StringUtil.capitalize(theseFilesMessage)
                        + " " + (nonProjectFiles.size() > 1 ? "do" : "does")
                        + " not belong to the project:");


    myUnlockOneButton.setSelected(true);
    setTextAndMnemonicAndListeners(myUnlockOneButton, "I want to edit " + theseFilesMessage + " anyway", "edit");

    int dirs = ContainerUtil.map2Set(nonProjectFiles, VirtualFile::getParent).size();
    setTextAndMnemonicAndListeners(myUnlockDirButton, "I want to edit all files in "
                                                      + StringUtil.pluralize("this", dirs)
                                                      + " " + StringUtil.pluralize("directory", dirs), "dir");

    setTextAndMnemonicAndListeners(myUnlockAllButton, "I want to edit any non-project file in the current session", "any");

    
    // disable default button to avoid accidental pressing, if user typed something, missed the dialog and pressed 'enter'.  
    getOKAction().putValue(DEFAULT_ACTION, null);
    getCancelAction().putValue(DEFAULT_ACTION, null);

    getRootPane().registerKeyboardAction(e -> doOKAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    getRootPane().registerKeyboardAction(e -> doOKAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    init();
  }

  private void setTextAndMnemonicAndListeners(JRadioButton button, String text, String mnemonic) {
    button.setText(text);
    button.setMnemonic(mnemonic.charAt(0));
    button.setDisplayedMnemonicIndex(button.getText().indexOf(mnemonic));
    
    // enabled OK button when user selects an option
    Runnable setDefaultButton = () -> {
      JRootPane rootPane = button.getRootPane();
      if (rootPane != null) rootPane.setDefaultButton(getButton(getOKAction()));
    };
    button.addActionListener(e -> setDefaultButton.run());
    button.addItemListener(e -> setDefaultButton.run());
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUnlockOneButton;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public NonProjectFileWritingAccessProvider.UnlockOption getUnlockOption() {
    if (myUnlockAllButton.isSelected()) return NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_ALL;
    if (myUnlockDirButton.isSelected()) return NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_DIR;
    return NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK;
  }

  protected String getHelpId() {
    return "Non-Project_Files_Access_Dialog";
  }
}
