// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ToolbarLabelAction extends DumbAwareAction implements CustomComponentAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //do nothing
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JBLabel label = new MyLabel(presentation)
      .withFont(JBUI.Fonts.toolbarFont())
      .withBorder(JBUI.Borders.empty(0, 6, 0, 5));

    if (isCopyable()) {
      label.setCopyable(true);
    }

    return label;
  }

  protected @Nullable HyperlinkListener createHyperlinkListener() {
    return null;
  }

  protected boolean isCopyable() {
    return false;
  }

  private class MyLabel extends JBLabel {
    @Override
    protected @NotNull HyperlinkListener createHyperlinkListener() {
      HyperlinkListener listener = ToolbarLabelAction.this.createHyperlinkListener();
      if (listener != null) return listener;

      return super.createHyperlinkListener();
    }

    @NotNull private final Presentation myPresentation;

    MyLabel(@NotNull Presentation presentation) {
      myPresentation = presentation;

      presentation.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent e) {
          String propertyName = e.getPropertyName();
          if (Presentation.PROP_TEXT.equals(propertyName) ||
              Presentation.PROP_DESCRIPTION.equals(propertyName) ||
              Presentation.PROP_ICON.equals(propertyName)) {
            updatePresentation();
          }
        }
      });
      updatePresentation();
    }

    private void updatePresentation() {
      setText(StringUtil.notNullize(myPresentation.getText()));
      setToolTipText(StringUtil.nullize(myPresentation.getDescription()));
      setIcon(myPresentation.getIcon());
    }
  }
}
