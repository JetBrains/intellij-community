package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author irengrig
 *         Date: 8/12/11
 *         Time: 3:12 PM
 */
public class FragmentNumberGutterIconRenderer extends GutterIconRenderer {
  private final int myNumber;
  private CaptionIcon myIcon;

  public FragmentNumberGutterIconRenderer(int number, final TextAttributesKey key, final Component component) {
    myNumber = number;
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final Color color = globalScheme.getAttributes(key).getBackgroundColor();

    myIcon = new CaptionIcon(color, UIUtil.getButtonFont(), String.valueOf(number), component, CaptionIcon.Form.ROUNDED, false, false);
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FragmentNumberGutterIconRenderer that = (FragmentNumberGutterIconRenderer)o;

    if (myNumber != that.myNumber) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myNumber;
  }
}
