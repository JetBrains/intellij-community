// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
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
  private JList<VirtualFile> myFileList;
  private JRadioButton myUnlockOneButton;
  private JRadioButton myUnlockDirButton;
  private JRadioButton myUnlockAllButton;

  public NonProjectFileWritingAccessDialog(@NotNull Project project, @NotNull List<? extends VirtualFile> nonProjectFiles) {
    super(project);
    setTitle(IdeBundle.message("dialog.title.non.project.files.protection"));

    myFileList.setPreferredSize(ReadOnlyStatusDialog.getDialogPreferredSize());

    myFileList.setCellRenderer(new FileListRenderer());
    myFileList.setModel(new CollectionListModel<>(nonProjectFiles));

    boolean dirsOnly = nonProjectFiles.stream().allMatch(VirtualFile::isDirectory);
    int size = nonProjectFiles.size();

    String listTitle = dirsOnly
                       ? IdeBundle.message("this.directory.does.not.belong.to.the.project", size)
                       : IdeBundle.message("this.file.does.not.belong.to.the.project", size);
    myListTitle.setText(listTitle);

    myUnlockOneButton.setSelected(true);
    String text = dirsOnly
                  ? IdeBundle.message("button.i.want.to.edit.choice.this.directory.anyway", size)
                  : IdeBundle.message("button.i.want.to.edit.choice.this.file.anyway", size);
    setTextAndMnemonicAndListeners(myUnlockOneButton, text, "edit");

    int dirsSize = ContainerUtil.map2Set(nonProjectFiles, VirtualFile::getParent).size();
    String dirsText = IdeBundle.message("button.i.want.to.edit.all.files.in.choice.this.directory", dirsSize);
    setTextAndMnemonicAndListeners(myUnlockDirButton, dirsText, "dir");

    setTextAndMnemonicAndListeners(myUnlockAllButton, IdeBundle.message("button.i.want.to.edit.any.non.project.file.in.current.session"), "any");

    getRootPane().registerKeyboardAction(e -> doOKAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    getRootPane().registerKeyboardAction(e -> doOKAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    init();
  }

  private void setTextAndMnemonicAndListeners(JRadioButton button, @NlsContexts.RadioButton String text, String mnemonic) {
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

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myUnlockOneButton;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  public @NotNull NonProjectFileWritingAccessProvider.UnlockOption getUnlockOption() {
    if (myUnlockAllButton.isSelected()) return NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_ALL;
    if (myUnlockDirButton.isSelected()) return NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_DIR;
    return NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK;
  }

  @Override
  protected String getHelpId() {
    return "Non-Project_Files_Access_Dialog";
  }
}
