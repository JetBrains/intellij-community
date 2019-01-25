// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorPopupHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Implementation of {@link EditorPopupHandler} showing a context menu for some {@link ActionGroup} (which can depend on click location).
 *
 * @since 2019.1
 */
public abstract class ContextMenuPopupHandler implements EditorPopupHandler {
  @Nullable
  public abstract ActionGroup getActionGroup(@NotNull EditorMouseEvent event);

  @Override
  public boolean handlePopup(@NotNull EditorMouseEvent event) {
    ActionGroup group = getActionGroup(event);
    if (group != null) {
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
      MouseEvent e = event.getMouseEvent();
      final Component c = e.getComponent();
      if (c != null && c.isShowing()) {
        JPopupMenu popupComponent = popupMenu.getComponent();
        disableHoverPopupsWhileShowing(event.getEditor(), popupComponent);
        popupComponent.show(c, e.getX(), e.getY());
        event.consume();
      }
    }
    return true;
  }

  private static void disableHoverPopupsWhileShowing(Editor editor, Component popupComponent) {
    new UiNotifyConnector.Once(popupComponent, new Activatable.Adapter() {
      @Override
      public void showNotify() {
        EditorMouseHoverPopupControl.disablePopups(editor);
        new UiNotifyConnector.Once(popupComponent, new Adapter() {
          @Override
          public void hideNotify() {
            EditorMouseHoverPopupControl.enablePopups(editor);
          }
        });
      }
    });
  }

  @Nullable
  private static ActionGroup getGroupForId(@Nullable String groupId) {
    return groupId == null ? null : ObjectUtils.tryCast(CustomActionsSchema.getInstance().getCorrectedAction(groupId), ActionGroup.class);
  }

  /**
   * {@link ContextMenuPopupHandler} specification, which uses an action group registered in {@link ActionManager} under given id.
   */
  public abstract static class ById extends ContextMenuPopupHandler {
    @Nullable
    @Override
    public ActionGroup getActionGroup(@NotNull EditorMouseEvent event) {
      return ContextMenuPopupHandler.getGroupForId(getActionGroupId(event));
    }

    @Nullable
    public abstract String getActionGroupId(@NotNull EditorMouseEvent event);
  }

  /**
   * Popup handler which always shows context menu for the same action group (regardless of mouse click location).
   */
  public static class Simple extends ContextMenuPopupHandler {
    private final ActionGroup myActionGroup;

    public Simple(ActionGroup actionGroup) {
      myActionGroup = actionGroup;
    }

    public Simple(String groupId) {
      this(ContextMenuPopupHandler.getGroupForId(groupId));
    }

    @Nullable
    @Override
    public ActionGroup getActionGroup(@NotNull EditorMouseEvent event) {
      return myActionGroup;
    }
  }
}
