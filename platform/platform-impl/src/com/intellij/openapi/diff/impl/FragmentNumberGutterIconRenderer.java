package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author irengrig
 *         Date: 8/12/11
 *         Time: 3:12 PM
 */
public class FragmentNumberGutterIconRenderer extends GutterIconRenderer implements DumbAware {
  private final String myPresentation;
  private CaptionIcon myIcon;

  public FragmentNumberGutterIconRenderer(String presentation, final TextAttributesKey key, final Component component, EditorEx editor) {
    myPresentation = presentation;
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    final Color color = globalScheme.getAttributes(key).getBackgroundColor();

    myIcon = new CaptionIcon(color, editor.getColorsScheme().getFont(EditorFontType.PLAIN),
                             presentation, component, CaptionIcon.Form.ROUNDED, false, false);
  }

  public void resetFont(final Editor editor) {
    myIcon.setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
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

    if (!myPresentation.equals(that.myPresentation)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPresentation.hashCode();
  }
}
