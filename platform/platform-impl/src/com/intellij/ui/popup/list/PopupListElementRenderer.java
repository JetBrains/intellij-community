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
package com.intellij.ui.popup.list;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListPopupStepEx;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PopupListElementRenderer<E> extends GroupedItemsListRenderer<E> {
  private final ListPopupImpl myPopup;
  private JLabel myShortcutLabel;

  public PopupListElementRenderer(final ListPopupImpl aPopup) {
    super(new ListItemDescriptorAdapter<E>() {
      @Override
      public String getTextFor(E value) {
        return aPopup.getListStep().getTextFor(value);
      }

      @Override
      public Icon getIconFor(E value) {
        return aPopup.getListStep().getIconFor(value);
      }

      @Override
      public boolean hasSeparatorAboveOf(E value) {
        return aPopup.getListModel().isSeparatorAboveOf(value);
      }

      @Override
      public String getCaptionAboveOf(E value) {
        return aPopup.getListModel().getCaptionAboveOf(value);
      }

      @Nullable
      @Override
      public String getTooltipFor(E value) {
        ListPopupStep<Object> listStep = aPopup.getListStep();
        if (!(listStep instanceof ListPopupStepEx)) return null;
        return ((ListPopupStepEx<E>)listStep).getTooltipTextFor(value);
      }
    });
    myPopup = aPopup;
  }

  @Override
  protected JComponent createItemComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    createLabel();
    panel.add(myTextLabel, BorderLayout.CENTER);
    myShortcutLabel = new JLabel();
    myShortcutLabel.setBorder(JBUI.Borders.empty(0, 0, 0, 3));
    Color color = UIManager.getColor("MenuItem.acceleratorForeground");
    myShortcutLabel.setForeground(color);
    panel.add(myShortcutLabel, BorderLayout.EAST);
    return layoutComponent(panel);
  }

  @Override
  protected void customizeComponent(JList<? extends E> list, E value, boolean isSelected) {
    ListPopupStep<Object> step = myPopup.getListStep();
    boolean isSelectable = step.isSelectable(value);
    myTextLabel.setEnabled(isSelectable);
    if (!isSelected && step instanceof BaseListPopupStep) {
      Color bg = ((BaseListPopupStep)step).getBackgroundFor(value);
      Color fg = ((BaseListPopupStep)step).getForegroundFor(value);
      if (fg != null) myTextLabel.setForeground(fg);
      if (bg != null) UIUtil.setBackgroundRecursively(myComponent, bg);
    }

    if (step.isMnemonicsNavigationEnabled()) {
      final int pos = step.getMnemonicNavigationFilter().getMnemonicPos(value);
      if (pos != -1) {
        String text = myTextLabel.getText();
        text = text.substring(0, pos) + text.substring(pos + 1);
        myTextLabel.setText(text);
        myTextLabel.setDisplayedMnemonicIndex(pos);
      }
    }
    else {
      myTextLabel.setDisplayedMnemonicIndex(-1);
    }

    if (step.hasSubstep(value) && isSelectable) {
      myNextStepLabel.setVisible(true);
      final boolean isDark = ColorUtil.isDark(UIUtil.getListSelectionBackground());
      myNextStepLabel.setIcon(isSelected ? isDark ? AllIcons.Icons.Ide.NextStepInverted
                                                  : AllIcons.Icons.Ide.NextStep
                                         : AllIcons.Icons.Ide.NextStepGrayed);
    }
    else {
      myNextStepLabel.setVisible(false);
      //myNextStepLabel.setIcon(PopupIcons.EMPTY_ICON);
    }

    setSelected(myNextStepLabel, isSelected);


    if (myShortcutLabel != null) {
      myShortcutLabel.setText("");
      if (value instanceof ShortcutProvider) {
        ShortcutSet set = ((ShortcutProvider)value).getShortcut();
        if (set != null) {
          Shortcut shortcut = ArrayUtil.getFirstElement(set.getShortcuts());
          if (shortcut != null) {
            myShortcutLabel.setText("     " + KeymapUtil.getShortcutText(shortcut));
          }
        }
      }
      setSelected(myShortcutLabel, isSelected);
      myShortcutLabel.setForeground(isSelected ? UIManager.getColor("MenuItem.acceleratorSelectionForeground") : UIManager.getColor("MenuItem.acceleratorForeground"));
    }
  }
}
