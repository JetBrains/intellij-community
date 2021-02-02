// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

public class PopupListElementRenderer<E> extends GroupedItemsListRenderer<E> {

  protected final ListPopupImpl myPopup;
  private JLabel myShortcutLabel;
  private @Nullable JLabel myValueLabel;

  protected JComponent myRightPart;
  protected JComponent myLeftPart;
  protected JComponent mySeparator;

  public PopupListElementRenderer(final ListPopupImpl aPopup) {
    super(new ListItemDescriptorAdapter<>() {
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
    createLabel();
    JPanel panel = new JPanel(new BorderLayout()) {
      private final AccessibleContext myAccessibleContext = myTextLabel.getAccessibleContext();

      @Override
      public AccessibleContext getAccessibleContext() {
        if (myAccessibleContext == null) {
          return super.getAccessibleContext();
        }
        return myAccessibleContext;
      }
    };
    panel.add(myTextLabel, BorderLayout.WEST);
    myValueLabel = new JLabel();
    myValueLabel.setEnabled(false);
    myValueLabel.setBorder(JBUI.Borders.empty(0, JBUIScale.scale(8), 1, 0));
    myValueLabel.setForeground(UIManager.getColor("MenuItem.acceleratorForeground"));
    panel.add(myValueLabel, BorderLayout.CENTER);
    myShortcutLabel = new JLabel();
    myShortcutLabel.setBorder(JBUI.Borders.emptyRight(3));
    myShortcutLabel.setForeground(UIManager.getColor("MenuItem.acceleratorForeground"));
    panel.add(myShortcutLabel, BorderLayout.EAST);
    return layoutComponent(panel);
  }

  @Override
  protected JComponent layoutComponent(JComponent middleItemComponent) {
    myNextStepLabel = new JLabel();
    myNextStepLabel.setOpaque(false);

    JPanel left = new JPanel(new BorderLayout());
    left.setBorder(JBUI.Borders.empty());
    left.add(middleItemComponent, BorderLayout.CENTER);

    JPanel right = new JPanel(new BorderLayout());
    int leftRightInset = (ListPopupImpl.NEXT_STEP_AREA_WIDTH - AllIcons.Icons.Ide.NextStep.getIconWidth()) / 2;
    right.setBorder(JBUI.Borders.empty(0, leftRightInset));
    right.add(myNextStepLabel, BorderLayout.CENTER);

    mySeparator = new JSeparator(SwingConstants.VERTICAL);

    JPanel result = new JPanel();
    result.setLayout(new GridBagLayout());
    result.setBorder(JBUI.Borders.empty());

    GridBag gbc = new GridBag()
      .setDefaultAnchor(0, GridBagConstraints.WEST)
      .setDefaultWeightX(0, 1)
      .setDefaultAnchor(GridBagConstraints.CENTER)
      .setDefaultWeightX(0)
      .setDefaultWeightY(1)
      .setDefaultPaddingX(0)
      .setDefaultPaddingY(0)
      .setDefaultInsets(0, 0, 0, 0)
      .setDefaultFill(GridBagConstraints.BOTH);

    result.add(left, gbc.next());
    result.add(mySeparator, gbc.next().insets(2, 0, 2, 0));
    result.add(right, gbc.next());

    myLeftPart = left;
    myRightPart = right;

    return result;
  }

  @Nullable
  @Override
  protected Icon getItemIcon(E value, boolean isSelected) {
    ListPopupStep<Object> step = myPopup.getListStep();
    return step.hasSubstep(value) && step.isFinal(value) && isNextStepButtonSelected(myPopup.getList())
           ? myDescriptor.getIconFor(value)
           : super.getItemIcon(value, isSelected);
  }

  @Override
  protected void customizeComponent(JList<? extends E> list, E value, boolean isSelected) {
    ListPopupStep<Object> step = myPopup.getListStep();
    boolean isSelectable = step.isSelectable(value);
    myTextLabel.setEnabled(isSelectable);

    setSelected(myComponent, isSelected && isSelectable);
    setSelected(myTextLabel, isSelected && isSelectable);
    myLeftPart.setOpaque(false);
    myRightPart.setOpaque(false);
    mySeparator.setVisible(false);

    boolean nextStepButtonSelected = false;
    if (step.hasSubstep(value)) {
      myNextStepLabel.setVisible(isSelectable);

      if (step.isFinal(value)) {
        myLeftPart.setOpaque(true);
        myRightPart.setOpaque(true);
        setSelected(myComponent, false);

        nextStepButtonSelected = isNextStepButtonSelected(list);
        setSelected(myLeftPart, isSelected && !nextStepButtonSelected);
        setSelected(myTextLabel, isSelected && !nextStepButtonSelected);
        setSelected(myRightPart, isSelected && nextStepButtonSelected);
        myNextStepLabel.setIcon(isSelectable & isSelected && nextStepButtonSelected ? AllIcons.Icons.Ide.NextStepInverted : AllIcons.Icons.Ide.NextStep);
        mySeparator.setVisible(true);
      }
      else {
        myNextStepLabel.setIcon(isSelectable & isSelected ? AllIcons.Icons.Ide.NextStepInverted : AllIcons.Icons.Ide.NextStep);
      }
    }
    else {
      myNextStepLabel.setVisible(false);
    }

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
      setSelected(myShortcutLabel, isSelected && isSelectable && !nextStepButtonSelected);
      myShortcutLabel.setForeground(isSelected && isSelectable && !nextStepButtonSelected
                                    ? UIManager.getColor("MenuItem.acceleratorSelectionForeground")
                                    : UIManager.getColor("MenuItem.acceleratorForeground"));
    }

    if (myValueLabel != null) {
      myValueLabel.setText(step instanceof ListPopupStepEx<?> ? ((ListPopupStepEx<E>)step).getValueFor(value) : null);
      setSelected(myValueLabel, isSelected && isSelectable  && !nextStepButtonSelected);
    }
  }

  private boolean isNextStepButtonSelected(JList<? extends E> list) {
    return list instanceof ListPopupImpl.NestedList && ((ListPopupImpl.NestedList)list).isNextStepButtonSelected();
  }
}
