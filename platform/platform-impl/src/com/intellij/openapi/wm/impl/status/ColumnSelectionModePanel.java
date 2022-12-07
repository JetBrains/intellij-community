// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.FocusUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

final class ColumnSelectionModePanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  static final @NonNls String SWING_FOCUS_OWNER_PROPERTY = "focusOwner";
  private TextPanel textPanel;

  ColumnSelectionModePanel(@NotNull Project project) {
    super(project);
  }

  @Override
  public @NotNull String ID() {
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
    if (textPanel == null) {
      textPanel = new TextPanel();
      textPanel.setVisible(false);
    }
    return textPanel;
  }

  @Override
  protected void registerCustomListeners(@NotNull MessageBusConnection connection) {
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateStatus();
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateStatus();
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        updateStatus();
      }
    });

    PropertyChangeListener propertyChangeListener = event -> {
      String propertyName = event.getPropertyName();
      if (EditorEx.PROP_INSERT_MODE.equals(propertyName) ||
          EditorEx.PROP_COLUMN_MODE.equals(propertyName) ||
          SWING_FOCUS_OWNER_PROPERTY.equals(propertyName)) {
        if (!getProject().isDisposed()) {
          updateStatus();
        }
      }
    };

    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    if (multicaster instanceof EditorEventMulticasterEx) {
      ((EditorEventMulticasterEx)multicaster).addPropertyChangeListener(propertyChangeListener, this);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      FocusUtil.addFocusOwnerListener(this, propertyChangeListener);
      updateStatus();
    }, getProject().getDisposed());
  }

  private void updateStatus() {
    if (textPanel == null) {
      return;
    }

    Editor editor = getFocusedEditor();
    if (editor != null && !isOurEditor(editor)) {
      return;
    }

    if (editor == null || !editor.isColumnMode()) {
      textPanel.setVisible(false);
    } 
    else {
      textPanel.setVisible(true);
      textPanel.setText(UIBundle.message("status.bar.column.status.text"));
      textPanel.setToolTipText(UIBundle.message("status.bar.column.status.tooltip.text"));
    }
  }
}
