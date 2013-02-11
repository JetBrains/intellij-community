package com.intellij.android.designer.designSurface.feedbacks;

import com.intellij.designer.designSurface.feedbacks.AbstractTextFeedback;
import com.intellij.ui.Colors;
import com.intellij.ui.LightColors;
import com.intellij.ui.SimpleTextAttributes;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TextFeedback extends AbstractTextFeedback {
  private static SimpleTextAttributes DIMENSION_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.lightGray);
  private static SimpleTextAttributes SNAP_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Colors.DARK_GREEN);

  public TextFeedback() {
    setBackground(LightColors.YELLOW);
  }

  public void dimension(String text) {
    append(text, DIMENSION_ATTRIBUTES);
  }

  public void snap(String text) {
    append(text, SNAP_ATTRIBUTES);
  }
}