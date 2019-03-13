// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This utility class is used to multiplex AA requests addressing JDK8 or earlier JDK versions.
 * Because since JDK9 the following {@code JComponent}'s client property mapping:
 * <pre>
 * {@code SwingUtilities2.AA_TEXT_PROPERTY_KEY -> SwingUtilities2.AATextInfo}
 * </pre>
 * was split into the pair (see JDK-6302464):
 * <pre>
 * {@code RenderingHints.KEY_TEXT_ANTIALIASING -> Object}
 * {@code RenderingHints.KEY_TEXT_LCD_CONTRAST -> Integer}
 * </pre>
 *
 * @author tav
 */
public class AATextInfo {
  public final @Nullable Object aaHint;
  public final @Nullable Integer lcdContrastHint;

  public AATextInfo(@Nullable Object aaHint, @Nullable Integer lcdContrastHint) {
    this.aaHint = aaHint;
    this.lcdContrastHint = lcdContrastHint;
  }

  public static Object create(@NotNull Object aaHint, @NotNull Integer lcdContrastHint) {
    if (SystemInfo.IS_AT_LEAST_JAVA9) {
      return new AATextInfo(aaHint, lcdContrastHint);
    }
    return UIUtilities.createAATextInfo(aaHint, lcdContrastHint);
  }

  public static void putClientProperty(@Nullable Object aaTextInfo, @NotNull JComponent comp) {
    if (SystemInfo.IS_AT_LEAST_JAVA9) {
      AATextInfo info = (AATextInfo)ObjectUtils.notNull(aaTextInfo, new AATextInfo(null, null));
      comp.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, info.aaHint);
      comp.putClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST, info.lcdContrastHint);
    } else {
      comp.putClientProperty(UIUtilities.AA_TEXT_PROPERTY_KEY, aaTextInfo);
    }
  }

  public static Object getClientProperty(@NotNull JComponent comp) {
    if (SystemInfo.IS_AT_LEAST_JAVA9) {
      Object aaHint = comp.getClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING);
      Object lcdContrastHint = comp.getClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST);
      return new AATextInfo(aaHint, lcdContrastHint != null ? (Integer)lcdContrastHint : null);
    }
    return comp.getClientProperty(UIUtilities.AA_TEXT_PROPERTY_KEY);
  }

  public static Pair<Object, Object>[] toArray(@Nullable Object aaTextInfo) {
    if (SystemInfo.IS_AT_LEAST_JAVA9) {
      AATextInfo info = (AATextInfo)ObjectUtils.notNull(aaTextInfo, new AATextInfo(null, null));
      //noinspection unchecked
      return new Pair[] {
        Pair.create(RenderingHints.KEY_TEXT_ANTIALIASING, info.aaHint),
        Pair.create(RenderingHints.KEY_TEXT_LCD_CONTRAST, info.lcdContrastHint)
      };
    }
    //noinspection unchecked
    return new Pair[] {Pair.create(UIUtilities.AA_TEXT_PROPERTY_KEY, aaTextInfo)};
  }
}
