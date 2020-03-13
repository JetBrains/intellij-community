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
  private static @NotNull Icon load(@NotNull String path) {
    return IconManager.getInstance().getIcon(path, PlatformDebuggerImplIcons.class);
  }


  public final static class Actions {
    /** 16x16 */ public static final @NotNull Icon Force_run_to_cursor = load("/icons/actions/force_run_to_cursor.svg");
    /** 16x16 */ public static final @NotNull Icon Force_step_into = load("/icons/actions/force_step_into.svg");
    /** 16x16 */ public static final @NotNull Icon Force_step_over = load("/icons/actions/force_step_over.svg");

  }

  public final static class MemoryView {
    /** 16x16 */ public static final @NotNull Icon Active = load("/icons/memoryView/active.svg");

  }

  public final static class PinToTop {
    /** 16x16 */ public static final @NotNull Icon PinnedItem = load("/icons/pinToTop/pinnedItem.svg");
    /** 16x16 */ public static final @NotNull Icon UnpinnedItem = load("/icons/pinToTop/unpinnedItem.svg");

  }
}
