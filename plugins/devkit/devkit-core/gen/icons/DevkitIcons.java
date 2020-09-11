// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class DevkitIcons {
  private static @NotNull Icon load(@NotNull String path, long cacheKey) {
    return IconManager.getInstance().loadRasterizedIcon(path, DevkitIcons.class, cacheKey);
  }

  /** 16x16 */ public static final @NotNull Icon Add_sdk = load("/icons/add_sdk.svg", -6463037881511944640L);

  public final static class Gutter {
    /** 12x12 */ public static final @NotNull Icon DescriptionFile = load("/icons/gutter/descriptionFile.svg", -3295941606072354211L);
    /** 12x12 */ public static final @NotNull Icon Diff = load("/icons/gutter/diff.svg", -5366171683069682269L);
    /** 12x12 */ public static final @NotNull Icon Plugin = load("/icons/gutter/plugin.svg", 2507814548087512074L);

  }
  /** 16x16 */ public static final @NotNull Icon Sdk_closed = load("/icons/sdk_closed.svg", -7368467361888522525L);
}
