// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.find.FindBundle;
import com.intellij.find.impl.FindPopupPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Bas Leijdekkers
 */
public class DirectoryComboBoxWithButtons extends JPanel {
  @NotNull private final ComboboxWithBrowseButton myDirectoryComboBox = new ComboboxWithBrowseButton(new ComboBox<String>(200));
  boolean myRecursive = true;
  BiConsumer<VirtualFile, Boolean> myCallback;

  private final ActionListener myListener = e -> {
    final VirtualFile directory = getDirectory();
    final JComboBox comboBox = myDirectoryComboBox.getComboBox();
    if (directory == null) {
      comboBox.putClientProperty("JComponent.outline", "error");
      final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("Not a directory", AllIcons.General.BalloonError,
                                                                                        MessageType.ERROR.getPopupBackground(), null).createBalloon();
      balloon.show(new RelativePoint(comboBox, new Point(comboBox.getWidth() / 2, 0)), Balloon.Position.above);
    }
    else {
      comboBox.putClientProperty("JComponent.outline", null);
    }
    if (myCallback != null) {
      myCallback.accept(directory, myRecursive);
    }
  };

  public DirectoryComboBoxWithButtons(@NotNull Project project) {
    super(new BorderLayout());

    @SuppressWarnings("unchecked") final ComboBox<String> comboBox = (ComboBox<String>)myDirectoryComboBox.getComboBox();
    myDirectoryComboBox.getComboBox().addActionListener(myListener);
    comboBox.setEditable(true);

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    final Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      final JTextField field = (JTextField)editorComponent;
      field.setColumns(40);
      FileChooserFactory.getInstance().installFileCompletion(field, descriptor, true, null);
    }
    comboBox.setMaximumRowCount(8);

    myDirectoryComboBox.addBrowseFolderListener(project, descriptor);

    final RecursiveAction recursiveDirectoryAction = new RecursiveAction();
    final int mnemonicModifiers = SystemInfo.isMac ? InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    recursiveDirectoryAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mnemonicModifiers)), myDirectoryComboBox);

    add(myDirectoryComboBox, BorderLayout.CENTER);
    add(FindPopupPanel.createToolbar(recursiveDirectoryAction), BorderLayout.EAST);
  }

  public void setCallback(BiConsumer<VirtualFile, Boolean> callback) {
    myCallback = callback;
  }

  public void setRecentDirectories(@NotNull List<String> recentDirectories) {
    @SuppressWarnings("unchecked") final JComboBox<String> comboBox = myDirectoryComboBox.getComboBox();
    comboBox.removeActionListener(myListener);
    comboBox.removeAllItems();
    for (int i = recentDirectories.size() - 1; i >= 0; i--) {
      comboBox.addItem(recentDirectories.get(i));
    }
    comboBox.addActionListener(myListener);
  }

  public void setDirectory(@NotNull VirtualFile directory) {
    setDirectory(directory.getPresentableUrl());
  }

  private void setDirectory(String path) {
    @SuppressWarnings("unchecked") final JComboBox<String> comboBox = myDirectoryComboBox.getComboBox();
    comboBox.removeItem(path);
    comboBox.insertItemAt(path, 0);
  }

  @Nullable
  public VirtualFile getDirectory() {
    @SuppressWarnings("unchecked") final JComboBox<String> comboBox = myDirectoryComboBox.getComboBox();
    final String directoryName = (String)comboBox.getSelectedItem();
    if (StringUtil.isEmptyOrSpaces(directoryName)) {
      return null;
    }

    final String path = FileUtil.toSystemIndependentName(directoryName);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile == null || !virtualFile.isDirectory() ? null : virtualFile;
  }

  public boolean isRecursive() {
    return myRecursive;
  }

  public void setRecursive(boolean recursive) {
    myRecursive = recursive;
  }

  private class RecursiveAction extends ToggleAction {
    RecursiveAction() {
      super(FindBundle.message("find.scope.directory.recursive.checkbox"), "Recursively", AllIcons.Actions.ShowAsTree);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myRecursive;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myRecursive = state;
      myCallback.accept(getDirectory(), myRecursive);
    }
  }
}

