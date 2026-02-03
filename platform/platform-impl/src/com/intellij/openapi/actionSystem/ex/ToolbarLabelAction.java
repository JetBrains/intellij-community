// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.HelpTooltip;
import com.intellij.ide.TooltipTitle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.MouseEvent;

public abstract class ToolbarLabelAction extends DumbAwareAction implements CustomComponentAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //do nothing
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JBLabel label = new MyLabel()
      .withFont(JBUI.Fonts.toolbarFont())
      .withBorder(JBUI.Borders.empty(0, 6, 0, 5));

    if (isCopyable()) {
      label.setCopyable(true);
    }

    return label;
  }

  @Override
  public void updateCustomComponent(@NotNull JComponent component, @NotNull Presentation presentation) {
    ((MyLabel)component).updateFromPresentation(presentation);
  }

  protected @Nullable HyperlinkListener createHyperlinkListener() {
    return null;
  }

  protected boolean isCopyable() {
    return false;
  }

  protected @Nullable @TooltipTitle String getHyperlinkTooltip() { return null; }

  private static final class MyLinkTooltip extends HelpTooltip {
    void mouseEntered(@NotNull MouseEvent e) {
      myMouseListener.mouseEntered(e);
    }

    void mouseExited(@NotNull MouseEvent e) {
      myMouseListener.mouseExited(e);
    }

    void mouseMoved(@NotNull MouseEvent e) {
      myMouseListener.mouseMoved(e);
    }
  }

  private final class MyLabel extends JBLabel {
    @Override
    protected @NotNull HyperlinkListener createHyperlinkListener() {
      HyperlinkListener listener = ToolbarLabelAction.this.createHyperlinkListener();
      if (listener != null) return installHyperlinkTooltip(listener);

      return installHyperlinkTooltip(super.createHyperlinkListener());
    }

    void updateFromPresentation(@NotNull Presentation presentation) {
      setText(StringUtil.notNullize(presentation.getText()));
      setToolTipText(StringUtil.nullize(presentation.getDescription()));
      setIcon(presentation.getIcon());
    }

    @NotNull HyperlinkListener installHyperlinkTooltip(@NotNull HyperlinkListener delegate) {
      String tooltipText = getHyperlinkTooltip();
      if (StringUtil.isEmptyOrSpaces(tooltipText)) return delegate;

      MyLinkTooltip tooltip = (MyLinkTooltip)new MyLinkTooltip().setTitle(tooltipText)
        .setShortcut(KeymapUtil.getFirstKeyboardShortcutText(ToolbarLabelAction.this));
      tooltip.installOn(this);

      return new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getInputEvent() instanceof MouseEvent mouseEvent) {
            switch (mouseEvent.getID()) {
              case MouseEvent.MOUSE_ENTERED -> tooltip.mouseEntered(mouseEvent);
              case MouseEvent.MOUSE_EXITED -> tooltip.mouseExited(mouseEvent);
              case MouseEvent.MOUSE_MOVED -> tooltip.mouseMoved(mouseEvent);
            }
          }

          delegate.hyperlinkUpdate(e);
        }
      };
    }
  }
}
