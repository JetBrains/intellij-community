// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionGroupUtil {

  /** @deprecated use {@link #isGroupEmpty(ActionGroup, AnActionEvent)} instead */
  @Deprecated
  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e, boolean unused) {
    return getActiveActions(actionGroup, e).isEmpty();
  }

  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return getActiveActions(actionGroup, e).isEmpty();
  }

  @Nullable
  public static AnAction getSingleActiveAction(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return getActiveActions(actionGroup, e).single();
  }

  @NotNull
  public static JBIterable<? extends AnAction> getActiveActions(@NotNull ActionGroup actionGroup,
                                                                @NotNull AnActionEvent e) {
    UpdateSession updater = Utils.getOrCreateUpdateSession(e);
    return JBIterable.from(updater.expandedChildren(actionGroup))
      .filter(o -> !(o instanceof Separator) && updater.presentation(o).isEnabledAndVisible());
  }

}
