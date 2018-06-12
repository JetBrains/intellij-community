// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListPopupStepEx;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PopupListElementRenderer<E> extends GroupedItemsListRenderer<E> {
  protected final ListPopupImpl myPopup;
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
      public Icon getSelectedIconFor(E value) {
        return aPopup.getListStep().getSelectedIconFor(value);
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
    myShortcutLabel.setBorder(JBUI.Borders.emptyRight(3));
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
    if (step instanceof BaseListPopupStep) {
      Color bg = ((BaseListPopupStep<E>)step).getBackgroundFor(value);
      Color fg = ((BaseListPopupStep<E>)step).getForegroundFor(value);
      if (!isSelected && fg != null) myTextLabel.setForeground(fg);
      if (!isSelected && bg != null) UIUtil.setBackgroundRecursively(myComponent, bg);
      if (bg != null && mySeparatorComponent.isVisible() && myCurrentIndex > 0) {
        E prevValue = list.getModel().getElementAt(myCurrentIndex - 1);
        // separator between 2 colored items shall get color too
        if (Comparing.equal(bg, ((BaseListPopupStep<E>)step).getBackgroundFor(prevValue))) {
          myRendererComponent.setBackground(bg);
        }
      }
    }

    if (step.isMnemonicsNavigationEnabled()) {
      MnemonicNavigationFilter<Object> filter = step.getMnemonicNavigationFilter();
      int pos = filter == null ? -1 : filter.getMnemonicPos(value);
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
      myShortcutLabel.setEnabled(isSelectable);
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
