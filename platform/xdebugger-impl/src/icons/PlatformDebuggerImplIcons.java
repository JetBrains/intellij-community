// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class PlatformDebuggerImplIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PlatformDebuggerImplIcons.class.getClassLoader(), cacheKey, flags);
  }

  public static final class Actions {
    /** 16x16 */ public static final @NotNull Icon Force_run_to_cursor = load("icons/actions/force_run_to_cursor.svg", 1895215693, 2);
    /** 16x16 */ public static final @NotNull Icon Force_step_into = load("icons/actions/force_step_into.svg", 1936387956, 2);
    /** 16x16 */ public static final @NotNull Icon Force_step_over = load("icons/actions/force_step_over.svg", 1378712635, 2);
  }

  public static final class MemoryView {
    /** 16x16 */ public static final @NotNull Icon Active = load("icons/memoryView/active.svg", -1093889567, 2);
  }

  public static final class PinToTop {
    /** 16x16 */ public static final @NotNull Icon PinnedItem = load("icons/pinToTop/pinnedItem.svg", 1865004942, 0);
    /** 16x16 */ public static final @NotNull Icon UnpinnedItem = load("icons/pinToTop/unpinnedItem.svg", 968867799, 0);
  }
}
