// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory.TransparentWrapper;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionGroupUtil {

  /** @see #getActiveActions(ActionGroup, AnActionEvent) */
  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return getActiveActions(actionGroup, e).isEmpty();
  }

  /** @see #getActiveActions(ActionGroup, AnActionEvent) */
  public static @Nullable AnAction getSingleActiveAction(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return getActiveActions(actionGroup, e).single();
  }

  /**
   * Requires proper {@link UpdateSession}.
   * Must be called on background thread (except {@link AnAction#beforeActionPerformedUpdate(AnActionEvent)}).
   * Not intended for {@link AnAction#actionPerformed(AnActionEvent)}.
   */
  public static @NotNull JBIterable<? extends AnAction> getActiveActions(@NotNull ActionGroup actionGroup,
                                                                         @NotNull AnActionEvent e) {
    UpdateSession session = e.getUpdateSession();
    return JBIterable.from(session.expandedChildren(actionGroup))
      .filter(o -> !(o instanceof Separator) && session.presentation(o).isEnabledAndVisible());
  }

  /**
   * Requires proper {@link UpdateSession}.
   * Must be called on background thread (except {@link AnAction#beforeActionPerformedUpdate(AnActionEvent)}).
   * Not intended for {@link AnAction#actionPerformed(AnActionEvent)}.
   */
  public static @NotNull JBIterable<? extends AnAction> getVisibleActions(@NotNull ActionGroup actionGroup,
                                                                          @NotNull AnActionEvent e) {
    UpdateSession session = e.getUpdateSession();
    return JBIterable.from(session.expandedChildren(actionGroup))
      .filter(o -> !(o instanceof Separator) && session.presentation(o).isVisible());
  }

  @ApiStatus.Experimental
  public static @NotNull ActionGroup forceHideDisabledChildren(@NotNull ActionGroup actionGroup) {
    final class Compact extends ActionGroupWrapper implements TransparentWrapper {

      Compact(@NotNull ActionGroup action) {
        super(action);
      }

      @Override
      @NotNull Presentation createTemplatePresentation() {
        Presentation presentation = super.createTemplatePresentation();
        presentation.putClientProperty(ActionUtil.HIDE_DISABLED_CHILDREN, true);
        return presentation;
      }
    }
    return actionGroup instanceof CompactActionGroup ? actionGroup : new Compact(actionGroup);
  }

  @ApiStatus.Experimental
  public static @NotNull ActionGroup forceRecursiveUpdateInBackground(@NotNull ActionGroup actionGroup) {
    final class MyGroup extends ActionGroup implements ActionUpdateThreadAware.Recursive, TransparentWrapper {
      {
        setPopup(false);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[] { actionGroup };
      }
    }
    return actionGroup instanceof ActionUpdateThreadAware.Recursive ? actionGroup : new MyGroup();
  }
}
