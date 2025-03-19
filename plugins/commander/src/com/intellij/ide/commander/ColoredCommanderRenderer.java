// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.commander;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class ColoredCommanderRenderer extends ColoredListCellRenderer {
  private final CommanderPanel myCommanderPanel;

  ColoredCommanderRenderer(final @NotNull CommanderPanel commanderPanel) {
    myCommanderPanel = commanderPanel;
  }

  @Override
  public Component getListCellRendererComponent(final JList list, final Object value, final int index, boolean selected, boolean hasFocus){
    hasFocus = selected; // border around inactive items

    if (!myCommanderPanel.isActive()) {
      selected = false;
    }

    return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
  }

  @Override
  protected void customizeCellRenderer(final @NotNull JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
    Color color = UIUtil.getListForeground();
    SimpleTextAttributes attributes = null;
    String locationString = null;

    setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0)); // for separator, see below

    if (value instanceof NodeDescriptor descriptor) {
      setIcon(descriptor.getIcon());
      final Color elementColor = descriptor.getColor();

      if (elementColor != null) {
        color = elementColor;
      }

      if (descriptor instanceof AbstractTreeNode treeNode) {
        final TextAttributesKey attributesKey = treeNode.getPresentation().getTextAttributesKey();

        if (attributesKey != null) {
          final TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);

          if (textAttributes != null) attributes =  SimpleTextAttributes.fromTextAttributes(textAttributes);
        }
        locationString = treeNode.getPresentation().getLocationString();

        final PresentationData presentation = treeNode.getPresentation();
        if (presentation.hasSeparatorAbove() && !selected) {
          setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                                                       BorderFactory.createEmptyBorder(0, 0, 1, 0)));
        }
      }
    }

    if(attributes == null) attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color);
    //noinspection HardCodedStringLiteral
    final String text = value.toString();

    if (myCommanderPanel.isEnableSearchHighlighting()) {
      JList list1 = myCommanderPanel.getList();
      if (list1 != null) {
        SpeedSearchUtil.appendFragmentsForSpeedSearch(list1, text, attributes, selected, this);
      }
    }
    else {
      append(text != null ? text : "", attributes);
    }

    if (locationString != null && !locationString.isEmpty()) {
      append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
