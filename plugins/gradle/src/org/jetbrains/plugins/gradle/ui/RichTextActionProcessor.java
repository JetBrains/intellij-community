/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.ui.ClickListener;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.*;
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
    
    if (presentation.getIcon() != null) {
      return new ActionButton(action, presentation.clone(), GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, JBUI.emptySize()) {
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
        final int y = g.getClipBounds().height - getFontMetrics(getFont()).getDescent() + 2;
        final int width = getFontMetrics(getFont()).stringWidth(getText());
        g.drawLine(0, y, width, y);
      }
    };
    Color color = UIUtil.isUnderDarcula() ? Color.ORANGE : Color.BLUE;
    result.setForeground(color);
    result.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        final AsyncResult<DataContext> callback = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = callback.getResult();
        if (context == null) {
          return false;
        }
        final Presentation presentation = new PresentationFactory().getPresentation(action);
        action.actionPerformed(new AnActionEvent(
          e, context, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, presentation, ActionManager.getInstance(), e.getModifiers()
        ));
        return true;
      }
    }.installOn(result);
    return result;
  }

  @NotNull
  @Override
  public String getKey() {
    return "action";
  }
}
