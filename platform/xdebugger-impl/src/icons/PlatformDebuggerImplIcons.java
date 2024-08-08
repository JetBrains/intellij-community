// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class PlatformDebuggerImplIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PlatformDebuggerImplIcons.class.getClassLoader(), cacheKey, flags);
  }

  public static final class Actions {
    /** 16x16 */ public static final @NotNull Icon DebuggerSync = load("icons/actions/debuggerSync.svg", -370007676, 2);
  }

  public static final class MemoryView {
    /** 16x16 */ public static final @NotNull Icon Active = load("icons/memoryView/active.svg", 1852454030, 2);
  }

  public static final class PinToTop {
    /** 16x16 */ public static final @NotNull Icon PinnedItem = load("icons/pinToTop/pinnedItem.svg", -570469980, 0);
    /** 16x16 */ public static final @NotNull Icon UnpinnedItem = load("icons/pinToTop/unpinnedItem.svg", -531132107, 0);
  }
}
