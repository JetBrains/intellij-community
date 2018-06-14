// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.find.FindBundle;
import com.intellij.find.impl.FindPopupPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class DirectoryComboBoxWithButtons extends JPanel {
  @NotNull private final Project myProject;
  @NotNull private final ComboBox<String> myDirectoryComboBox;
  private boolean myRecursive = true;

  @SuppressWarnings("WeakerAccess")
  public DirectoryComboBoxWithButtons(@NotNull Project project) {
    super(new BorderLayout());

    myProject = project;
    myDirectoryComboBox = new ComboBox<>(200);
    myDirectoryComboBox.setEditable(true);

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    Component editorComponent = myDirectoryComboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      JTextField field = (JTextField)editorComponent;
      field.setColumns(40);
      FileChooserFactory.getInstance().installFileCompletion(field, descriptor, true, null);
    }
    myDirectoryComboBox.setMaximumRowCount(8);

    FixedSizeButton mySelectDirectoryButton = new FixedSizeButton(myDirectoryComboBox);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(mySelectDirectoryButton, myDirectoryComboBox);
    mySelectDirectoryButton.setMargin(JBUI.emptyInsets());

    mySelectDirectoryButton.addActionListener(__ -> {
      FileChooser.chooseFiles(descriptor, myProject, null, null,
                              new FileChooser.FileChooserConsumer() {
                                @Override
                                public void consume(List<VirtualFile> files) {
                                  ApplicationManager.getApplication().invokeLater(() -> {
                                    IdeFocusManager.getInstance(myProject).requestFocus(myDirectoryComboBox.getEditor().getEditorComponent(), true);
                                    myDirectoryComboBox.getEditor().setItem(files.get(0).getPresentableUrl());
                                  });
                                }

                                @Override
                                public void cancelled() {
                                  ApplicationManager.getApplication().invokeLater(() -> {
                                    IdeFocusManager.getInstance(myProject).requestFocus(myDirectoryComboBox.getEditor().getEditorComponent(), true);
                                  });
                                }
                              });
    });

    RecursiveAction
      recursiveDirectoryAction = new RecursiveAction();
    int mnemonicModifiers = SystemInfo.isMac ? InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    recursiveDirectoryAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mnemonicModifiers)), myDirectoryComboBox);

    add(myDirectoryComboBox, BorderLayout.CENTER);
    JPanel buttonsPanel = new JPanel(new GridLayout(1, 2));
    buttonsPanel.add(mySelectDirectoryButton);
    buttonsPanel.add(FindPopupPanel.createToolbar(recursiveDirectoryAction).getComponent()); //check if toolbar updates the button with no delays
    add(buttonsPanel, BorderLayout.EAST);
  }

  public void init(@Nullable String currentDirectory, @NotNull List<String> recentDirectories) {
    if (myDirectoryComboBox.getItemCount() > 0) {
      myDirectoryComboBox.removeAllItems();
    }
    if (currentDirectory != null && !currentDirectory.isEmpty()) {
      recentDirectories.remove(currentDirectory);
      myDirectoryComboBox.addItem(currentDirectory);
    }
    for (int i = recentDirectories.size() - 1; i >= 0; i--) {
      myDirectoryComboBox.addItem(recentDirectories.get(i));
    }
    if (myDirectoryComboBox.getItemCount() == 0) {
      myDirectoryComboBox.addItem("");
    }
  }

  public void setCurrentDirectory(@NotNull VirtualFile directory) {
    final String text = directory.getPresentableUrl();
    final int count = myDirectoryComboBox.getItemCount();
    for (int i = 0; i < count; i++) {
      if (text.equals(myDirectoryComboBox.getItemAt(i))) {
        myDirectoryComboBox.removeItemAt(i);
      }
    }
    myDirectoryComboBox.setSelectedItem(text);
  }

  @Nullable
  public VirtualFile getDirectory() {
    String directoryName = (String)myDirectoryComboBox.getSelectedItem();
    if (StringUtil.isEmptyOrSpaces(directoryName)) {
      return null;
    }

    String path = FileUtil.toSystemIndependentName(directoryName);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null || !virtualFile.isDirectory()) {
      virtualFile = null;
      @SuppressWarnings("deprecation") VirtualFileSystem[] fileSystems = ApplicationManager.getApplication().getComponents(VirtualFileSystem.class);
      for (VirtualFileSystem fs : fileSystems) {
        if (fs instanceof LocalFileProvider) {
          @SuppressWarnings("deprecation") VirtualFile file = ((LocalFileProvider)fs).findLocalVirtualFileByPath(path);
          if (file != null && file.isDirectory()) {
            if (file.getChildren().length > 0) {
              virtualFile = file;
              break;
            }
            if (virtualFile == null) {
              virtualFile = file;
            }
          }
        }
      }
    }
    return virtualFile;
  }

  public boolean isRecursive() {
    return myRecursive;
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
      myRecursive = true;
    }
  }
}

