// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class ColumnSelectionModePanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget, PropertyChangeListener {
  private final TextPanel myTextPanel = new TextPanel();

  public ColumnSelectionModePanel(@NotNull Project project) {
    super(project);
    myTextPanel.setVisible(false);
  }

  @Override
  @NotNull
  public String ID() {
    return StatusBar.StandardWidgets.COLUMN_SELECTION_MODE_PANEL;
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public StatusBarWidget copy() {
    return new ColumnSelectionModePanel(getProject());
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
    if (!myProject.isDisposed()) return;
    final Editor editor = getFocusedEditor();
    if (editor != null && !isOurEditor(editor)) return;
    if (editor == null || !editor.isColumnMode()) {
      myTextPanel.setVisible(false);
    } 
    else {
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
