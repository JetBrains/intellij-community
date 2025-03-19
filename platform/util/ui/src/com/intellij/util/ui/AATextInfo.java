// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

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
 * was split into the pair (see <a href="https://bugs.openjdk.org/browse/JDK-6302464">JDK-6302464</a>):
 * <pre>
 * {@code RenderingHints.KEY_TEXT_ANTIALIASING -> Object}
 * {@code RenderingHints.KEY_TEXT_LCD_CONTRAST -> Integer}
 * </pre>
 *
 * @author tav
 */
public final class AATextInfo {
  public final @Nullable Object aaHint;
  public final @Nullable Integer lcdContrastHint;

  public AATextInfo(@Nullable Object aaHint, @Nullable Integer lcdContrastHint) {
    this.aaHint = aaHint;
    this.lcdContrastHint = lcdContrastHint;
  }

  public static void putClientProperty(@Nullable AATextInfo aaTextInfo, @NotNull JComponent component) {
    AATextInfo info = ObjectUtils.notNull(aaTextInfo, new AATextInfo(null, null));
    component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, info.aaHint);
    component.putClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST, info.lcdContrastHint);
  }

  public static @NotNull AATextInfo getClientProperty(@NotNull JComponent component) {
    Object aaHint = component.getClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING);
    Object lcdContrastHint = component.getClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST);
    return new AATextInfo(aaHint, lcdContrastHint != null ? (Integer)lcdContrastHint : null);
  }
}
