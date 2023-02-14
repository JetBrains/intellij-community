// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.fields.ExpandableSupport;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class ExtendableEditorSupport {
  private static final Key<JPanel> BUTTON_CONTAINER = Key.create("EditorButtonContainer");
  
  public static void setupExtension(@NotNull EditorEx editor,
                                     Color background,
                                     ExtendableTextComponent.Extension extension) {
    JLabel label = ExpandableSupport.createLabel(extension);
    label.setBorder(JBUI.Borders.emptyLeft(2));
    editor.getScrollPane().setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    JScrollBar scrollBar = editor.getScrollPane().getVerticalScrollBar();
    JPanel panel = ClientProperty.get(scrollBar, BUTTON_CONTAINER);
    if (panel == null) {
      panel = new JPanel(new HorizontalLayout(4));
      panel.setOpaque(false);
      ClientProperty.put(scrollBar, BUTTON_CONTAINER, panel);
      scrollBar.setBackground(background);
      scrollBar.add(JBScrollBar.LEADING, panel);
      scrollBar.setOpaque(true);
    }
    panel.add(label);
  }
}
