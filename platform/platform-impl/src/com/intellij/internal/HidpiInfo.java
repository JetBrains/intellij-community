/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author tav
 */
public class HidpiInfo extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent anActionEvent) {
    final String mode = UIUtil.isJreHiDPIEnabled() ? "enabled" : "disabled";

    List<Pair<String, String>> values = new ArrayList<>();
    values.add(Pair.create("JRE HiDPI", mode));
    values.add(Pair.create("User scale", "" + JBUI.scale(1f)));
    values.add(Pair.create("System scale", "" + JBUI.sysScale(IdeFrameImpl.getActiveFrame())));

    final int KEY_MIN_WIDTH = GraphicsUtil.stringWidth("System scale", UIUtil.getLabelFont()) + JBUI.scale(8);
    final int VAL_MIN_WIDTH = GraphicsUtil.stringWidth(mode, UIUtil.getLabelFont()) + JBUI.scale(8);

    ListPopupImpl popup = new ListPopupImpl(new BaseListPopupStep<>("HiDPI Info", values)) {
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new ListCellRenderer() {
          Border myBorder = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1 ), JBUI.Borders.empty(2));

          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Pair<String, String> pair = (Pair<String, String>)value;
            JPanel panel = new JPanel();
            panel.setLayout(new FlowLayout());
            panel.add(label(pair.first, KEY_MIN_WIDTH));
            panel.add(label(pair.second, VAL_MIN_WIDTH));
            return panel;
          }

          JLabel label(String value, int width) {
            JLabel label = new JLabel(value);
            label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
            label.setBorder(myBorder);
            return label;
          }
        };
      }
    };

    popup.getList().setBackground(UIManager.getColor("Panel.background"));
    popup.getList().setSelectionModel(new DefaultListSelectionModel() {
      @Override
      public boolean isSelectedIndex(int i) {
        return false;
      }
      @Override
      public boolean isSelectionEmpty() {
        return true;
      }
    });
    popup.showInCenterOf(IdeFrameImpl.getActiveFrame());
  }
}
