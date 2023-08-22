// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.function.Function;

final class BidiContentNotificationProvider implements EditorNotificationProvider {
  private static final Key<Boolean> DISABLE_NOTIFICATION = Key.create("bidi.content.notification.disable");

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                @NotNull VirtualFile file) {
    if (PropertiesComponent.getInstance().isTrueValue(DISABLE_NOTIFICATION.toString())) {
      return null;
    }

    return fileEditor -> {
      if (!(fileEditor instanceof TextEditor)) {
        return null;
      }

      Editor editor = ((TextEditor)fileEditor).getEditor();
      if (!Boolean.TRUE.equals(editor.getUserData(EditorImpl.CONTAINS_BIDI_TEXT)) ||
          Boolean.TRUE.equals(editor.getUserData(DISABLE_NOTIFICATION))) {
        return null;
      }

      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
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
    };
  }

  private static void showChooserPopup(Editor editor) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction group = actionManager.getAction(IdeActions.GROUP_EDITOR_BIDI_TEXT_DIRECTION);
    if (group instanceof ActionGroup) {
      JPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.MAIN_MENU, (ActionGroup)group).getComponent();
      AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
      if (event instanceof MouseEvent && ((MouseEvent)event).getComponent().isShowing()) {
        JBPopupMenu.showByEvent((MouseEvent)event, popupMenu);
      }
      else {
        JBPopupMenu.showByEditor(editor, popupMenu);
      }
    }
  }
}
