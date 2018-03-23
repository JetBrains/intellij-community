// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.list.IconListPopupRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class PopupListElementRendererWithIcon extends PopupListElementRenderer<Object> implements IconListPopupRenderer {
  protected IconComponent myIconLabel;

  public PopupListElementRendererWithIcon(ListPopupImpl aPopup) {
    super(aPopup);
  }

  @Override
  public boolean isIconAt(@NotNull Point point) {
    JList list = myPopup.getList();
    int index = myPopup.getList().locationToIndex(point);
    Rectangle bounds = myPopup.getList().getCellBounds(index, index);
    Component renderer = getListCellRendererComponent(list, list.getSelectedValue(), index, true, true);
    renderer.setBounds(bounds);
    renderer.doLayout();
    point.translate(-bounds.x, -bounds.y);
    return SwingUtilities.getDeepestComponentAt(renderer, point.x, point.y) instanceof IconComponent;
  }

  @Override
  protected void customizeComponent(JList<?> list, Object value, boolean isSelected) {
    super.customizeComponent(list, value, isSelected);
    myTextLabel.setIcon(null);
    myTextLabel.setDisabledIcon(null);
    myIconLabel.setIcon(isSelected ? myDescriptor.getSelectedIconFor(value) : myDescriptor.getIconFor(value));
  }

  @Override
  protected JComponent createItemComponent() {
    myTextLabel = new ErrorLabel();
    myTextLabel.setOpaque(true);
    myTextLabel.setBorder(JBUI.Borders.empty(1));

    myIconLabel = new IconComponent();

    JPanel panel = new OpaquePanel(new BorderLayout(), JBColor.WHITE);
    panel.add(myIconLabel, BorderLayout.WEST);
    panel.add(myTextLabel, BorderLayout.CENTER);
    return layoutComponent(panel);
  }

  public static class IconComponent extends JLabel {
  }
}
