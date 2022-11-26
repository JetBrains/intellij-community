// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.Utils;
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
    Utils.initUpdateSession(e); //todo remove me
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
    Utils.initUpdateSession(e); //todo remove me
    UpdateSession session = e.getUpdateSession();
    return JBIterable.from(session.expandedChildren(actionGroup))
      .filter(o -> !(o instanceof Separator) && session.presentation(o).isVisible());
  }

  @ApiStatus.Experimental
  public static @NotNull ActionGroup forceRecursiveUpdateInBackground(@NotNull ActionGroup actionGroup) {
    class MyGroup extends ActionGroup implements ActionUpdateThreadAware.Recursive {
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
    return new MyGroup();
  }
}
