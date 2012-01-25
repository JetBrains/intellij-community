package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 1/16/12 5:20 PM
 */
public class RichTextActionProcessor implements RichTextControlBuilder.RichTextProcessor {
  
  @Override
  public JComponent process(@NotNull String s) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(s);
    if (action == null) {
      return null;
    }
    final Presentation presentation = action.getTemplatePresentation();
    if (presentation == null) {
      return null;
    }
    return new ActionButton(action, presentation.clone(), GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, new Dimension(0, 0)) {
      @Override
      protected void paintButtonLook(Graphics g) {
        // Don't draw border at the inline button.
        ActionButtonLook look = getButtonLook();
        look.paintBackground(g, this);
        look.paintIcon(g, this, getIcon());
      }
    };
  }

  @NotNull
  @Override
  public String getKey() {
    return "action";
  }
}
