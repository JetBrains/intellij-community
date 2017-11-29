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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author cdr
 */
public class InsertOverwritePanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget, PropertyChangeListener {
  private final TextPanel myTextPanel = new TextPanel();

  public InsertOverwritePanel(Project project) {
    super(project);
    myTextPanel.setRightPadding(7);
  }

  @Override
  @NotNull
  public String ID() {
    return "InsertOverwrite";
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public StatusBarWidget copy() {
    return new InsertOverwritePanel(getProject());
  }

  @Override
  public JComponent getComponent() {
    return myTextPanel;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this);
    Disposer.register(this, 
                      () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, 
                                                                                                               this));
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    if (multicaster instanceof EditorEventMulticasterEx) {
      ((EditorEventMulticasterEx)multicaster).addPropertyChangeListener(this, this);
    }
  }

  private void updateStatus() {
    final Editor editor = getFocusedEditor();
    if (editor == null || !editor.isColumnMode()) {
      myTextPanel.setBorder(null);
      myTextPanel.setVisible(false);
    } else {
      myTextPanel.setBorder(WidgetBorder.INSTANCE);
      myTextPanel.setVisible(true);
      myTextPanel.setText(UIBundle.message("status.bar.column.status.text"));
      myTextPanel.setToolTipText("Column selection mode");
    }
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    updateStatus();
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    updateStatus();
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent evt) {
    String propertyName = evt.getPropertyName();
    if (EditorEx.PROP_INSERT_MODE.equals(propertyName) || 
        EditorEx.PROP_COLUMN_MODE.equals(propertyName) || 
        SWING_FOCUS_OWNER_PROPERTY.equals(propertyName)) {
      updateStatus();
    }
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    updateStatus();
  }
}
