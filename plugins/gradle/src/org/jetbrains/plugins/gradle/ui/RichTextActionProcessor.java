package org.jetbrains.plugins.gradle.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
    
    if (presentation.getIcon() != null) {
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

    final String text = action.getTemplatePresentation().getText();
    JLabel result = new JLabel(text) {
      public void paint(Graphics g) {
        super.paint(g);
        final int y = g.getClipBounds().height - getFontMetrics(getFont()).getDescent() + 1;
        final int width = getFontMetrics(getFont()).stringWidth(getText());
        g.drawLine(0, y, width, y);
      }
    };
    result.setForeground(UIUtil.getInactiveTextColor().darker().darker().darker());
    
    result.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final AsyncResult<DataContext> callback = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = callback.getResult();
        if (context == null) {
          return;
        }
        final Presentation presentation = new PresentationFactory().getPresentation(action);
        action.actionPerformed(new AnActionEvent(
          e, context, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, presentation, ActionManager.getInstance(), e.getModifiers()
        ));
      }
    });
    return result;
  }

  @NotNull
  @Override
  public String getKey() {
    return "action";
  }
}
