// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class BidiContentNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("bidi.content.notification.panel");
  private static final Key<Boolean> DISABLE_NOTIFICATION = Key.create("bidi.content.notification.disable");

  @Override
  public @NotNull Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (!(fileEditor instanceof TextEditor)) return null;
    Editor editor = ((TextEditor)fileEditor).getEditor();
    if (!Boolean.TRUE.equals(editor.getUserData(EditorImpl.CONTAINS_BIDI_TEXT)) ||
        Boolean.TRUE.equals(editor.getUserData(DISABLE_NOTIFICATION)) ||
        PropertiesComponent.getInstance().isTrueValue(DISABLE_NOTIFICATION.toString())) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
    panel.setText(EditorBundle.message("bidi.content.message"));
    panel.createActionLabel(EditorBundle.message("bidi.content.choose.message"), () -> showChooserPopup(editor));
    panel.createActionLabel(EditorBundle.message("notification.hide.message"), () -> {
      editor.putUserData(DISABLE_NOTIFICATION, Boolean.TRUE);
      EditorNotifications.getInstance(project).updateNotifications(file);
    });
    panel.createActionLabel(EditorBundle.message("notification.dont.show.again.message"), () -> {
      PropertiesComponent.getInstance().setValue(DISABLE_NOTIFICATION.toString(), "true");
      EditorNotifications.getInstance(project).updateAllNotifications();
    });
    return panel;
  }

  private static void showChooserPopup(Editor editor) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction group = actionManager.getAction(IdeActions.GROUP_EDITOR_BIDI_TEXT_DIRECTION);
    if (group instanceof ActionGroup) {
      JPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.MAIN_MENU, (ActionGroup)group).getComponent();
      AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
      if (event instanceof MouseEvent) {
        JBPopupMenu.showByEvent((MouseEvent)event, popupMenu);
      }
      else {
        JBPopupMenu.showByEditor(editor, popupMenu);
      }
    }
  }
}
