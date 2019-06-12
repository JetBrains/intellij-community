// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
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
  @NotNull private final ComponentWithBrowseButton<ComboBox<String>> myDirectoryComboBox =
    new ComponentWithBrowseButton<>(new ComboBox<>(200), null);
  private volatile boolean myUpdating = false;

  boolean myRecursive = true;
  Runnable myCallback;

  public DirectoryComboBoxWithButtons(@NotNull Project project) {
    super(new BorderLayout());

    final ComboBox<String> comboBox = myDirectoryComboBox.getChildComponent();
    comboBox.addActionListener(e -> {
      if (myUpdating) return;
      final VirtualFile directory = getDirectory();
      final ComboBox source = (ComboBox)e.getSource();
      if (directory == null) {
        source.putClientProperty("JComponent.outline", "error");
        final Balloon balloon = JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder("Not a directory", AllIcons.General.BalloonError, MessageType.ERROR.getPopupBackground(), null)
          .createBalloon();
        balloon.show(new RelativePoint(source, new Point(source.getWidth() / 2, 0)), Balloon.Position.above);
        source.requestFocus();
      }
      else {
        source.putClientProperty("JComponent.outline", null);
      }
      if (myCallback != null && directory != null) {
        myCallback.run();
      }
    });
    comboBox.setEditable(true);

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setForcedToUseIdeaFileChooser(true);
    final Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      FileChooserFactory.getInstance().installFileCompletion((JTextField)editorComponent, descriptor, true, null);
    }
    comboBox.setMaximumRowCount(8);

    myDirectoryComboBox.addBrowseFolderListener(null, null, project, descriptor, new TextComponentAccessor<ComboBox<String>>() {
      @Override
      public String getText(ComboBox comboBox) {
        return comboBox.getEditor().getItem().toString();
      }

      @Override
      public void setText(ComboBox component, @NotNull String text) {
        comboBox.getEditor().setItem(text);
      }
    });

    final RecursiveAction recursiveDirectoryAction = new RecursiveAction();
    final int mnemonicModifiers = SystemInfo.isMac ? InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    recursiveDirectoryAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mnemonicModifiers)), myDirectoryComboBox);

    add(myDirectoryComboBox, BorderLayout.CENTER);
    add(FindPopupPanel.createToolbar(recursiveDirectoryAction), BorderLayout.EAST);
  }

  public ComboBox<String> getComboBox() {
    return myDirectoryComboBox.getChildComponent();
  }

  public void setCallback(Runnable callback) {
    myCallback = callback;
  }

  public void setRecentDirectories(@NotNull List<String> recentDirectories) {
    final ComboBox<String> comboBox = myDirectoryComboBox.getChildComponent();
    myUpdating = true;
    try {
      comboBox.removeAllItems();
      for (int i = recentDirectories.size() - 1; i >= 0; i--) {
        comboBox.addItem(recentDirectories.get(i));
      }
    } finally {
      myUpdating = false;
    }
  }

  public void setDirectory(@NotNull VirtualFile directory) {
    final String url = directory.getPresentableUrl();
    final ComboBox<String> comboBox = myDirectoryComboBox.getChildComponent();
    comboBox.getEditor().setItem(url);
    setDirectory(url);
  }

  private void setDirectory(String path) {
    myDirectoryComboBox.getChildComponent().setSelectedItem(path);
  }

  @Nullable
  public VirtualFile getDirectory() {
    final ComboBox<String> comboBox = myDirectoryComboBox.getChildComponent();
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
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myRecursive;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myRecursive = state;
      myCallback.run();
    }
  }
}

