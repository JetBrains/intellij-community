// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class MoreInfoMessageDialog extends MessageDialog {
  @Nullable private final String myInfoText;

  public MoreInfoMessageDialog(Project project,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @Nullable String moreInfo,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               Icon icon) {
    super(project);
    myInfoText = moreInfo;
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
  }

  @Override
  protected JComponent createNorthPanel() {
    return doCreateCenterPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    if (myInfoText == null) {
      return null;
    }
    final JPanel panel = new JPanel(new BorderLayout());
    final JTextArea area = new JTextArea(myInfoText);
    area.setEditable(false);
    final JBScrollPane scrollPane = new JBScrollPane(area) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension preferredSize = super.getPreferredSize();
        final Container parent = getParent();
        if (parent != null) {
          return new Dimension(preferredSize.width, Math.min(150, preferredSize.height));
        }
        return preferredSize;
      }
    };
    panel.add(scrollPane);
    return panel;
  }
}
