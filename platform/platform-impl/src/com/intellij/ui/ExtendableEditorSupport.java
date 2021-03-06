// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.fields.ExpandableSupport;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class ExtendableEditorSupport {
  public static void setupExtension(@NotNull EditorEx editor,
                                     Color background,
                                     ExtendableTextComponent.Extension extension) {
    JLabel label = ExpandableSupport.createLabel(extension);
    label.setBorder(JBUI.Borders.emptyLeft(2));
    editor.getScrollPane().setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    editor.getScrollPane().getVerticalScrollBar().setBackground(background);
    editor.getScrollPane().getVerticalScrollBar().add(JBScrollBar.LEADING, label);
    editor.getScrollPane().getVerticalScrollBar().setOpaque(true);
  }
}
