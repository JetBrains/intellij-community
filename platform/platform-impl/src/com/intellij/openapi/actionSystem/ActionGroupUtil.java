// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionGroupUtil {

  /** @deprecated use {@link #isGroupEmpty(ActionGroup, AnActionEvent)} instead */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e, boolean unused) {
    return getActiveActions(actionGroup, e).isEmpty();
  }

  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return getActiveActions(actionGroup, e).isEmpty();
  }

  public static @Nullable AnAction getSingleActiveAction(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return getActiveActions(actionGroup, e).single();
  }

  public static @NotNull JBIterable<? extends AnAction> getActiveActions(@NotNull ActionGroup actionGroup,
                                                                         @NotNull AnActionEvent e) {
    UpdateSession updater = Utils.getOrCreateUpdateSession(e);
    return JBIterable.from(updater.expandedChildren(actionGroup))
      .filter(o -> !(o instanceof Separator) && updater.presentation(o).isEnabledAndVisible());
  }

  public static @NotNull JBIterable<? extends AnAction> getVisibleActions(@NotNull ActionGroup actionGroup,
                                                                          @NotNull AnActionEvent e) {
    UpdateSession updater = Utils.getOrCreateUpdateSession(e);
    return JBIterable.from(updater.expandedChildren(actionGroup))
      .filter(o -> !(o instanceof Separator) && updater.presentation(o).isVisible());
  }

  @ApiStatus.Experimental
  public static @NotNull ActionGroup forceRecursiveUpdateInBackground(@NotNull ActionGroup actionGroup) {
    class MyGroup extends ActionGroup implements UpdateInBackground.Recursive {
      @Override
      public boolean isPopup() {
        return false;
      }

      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[] { actionGroup };
      }
    }
    return new MyGroup();
  }
}
