// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class PlatformDebuggerImplIcons {
  private static @NotNull Icon load(@NotNull String path, long cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PlatformDebuggerImplIcons.class.getClassLoader(), cacheKey, flags);
  }

  public static final class Actions {
    /** 16x16 */ public static final @NotNull Icon Force_run_to_cursor = load("icons/actions/force_run_to_cursor.svg", 2147487336423957438L, 2);
    /** 16x16 */ public static final @NotNull Icon Force_step_into = load("icons/actions/force_step_into.svg", -453292801833307550L, 2);
    /** 16x16 */ public static final @NotNull Icon Force_step_over = load("icons/actions/force_step_over.svg", 8278059795978256151L, 2);
  }

  public static final class MemoryView {
    /** 16x16 */ public static final @NotNull Icon Active = load("icons/memoryView/active.svg", -6836016057379508591L, 2);
  }

  public static final class PinToTop {
    /** 16x16 */ public static final @NotNull Icon PinnedItem = load("icons/pinToTop/pinnedItem.svg", 8354161740444972522L, 0);
    /** 16x16 */ public static final @NotNull Icon UnpinnedItem = load("icons/pinToTop/unpinnedItem.svg", -3848211077360195458L, 0);
  }
}
