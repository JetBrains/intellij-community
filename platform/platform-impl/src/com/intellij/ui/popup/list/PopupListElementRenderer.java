// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListPopupStepEx;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.popup.NumericMnemonicItem;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

public class PopupListElementRenderer<E> extends GroupedItemsListRenderer<E> {

  public static final Key<@NlsSafe String> CUSTOM_KEY_STROKE_TEXT = new Key<>("CUSTOM_KEY_STROKE_TEXT");
  protected final ListPopupImpl myPopup;
  private JLabel myShortcutLabel;
  private @Nullable JLabel myValueLabel;
  protected JLabel myMnemonicLabel;
  protected JLabel myIconLabel;

  protected JComponent myButtonPane;
  protected JComponent myMainPane;
  protected JComponent myNextStepButtonSeparator;
  protected JComponent myIconBar;

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
  protected SeparatorWithText createSeparator() {
    Insets labelInsets = ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.separatorLabelInsets() :
                         getDefaultItemComponentBorder().getBorderInsets(new JLabel());
    GroupHeaderSeparator result = new GroupHeaderSeparator(labelInsets);
    if (ExperimentalUI.isNewUI()) {
      result.setBorder(new JBEmptyBorder(JBUI.CurrentTheme.Popup.separatorInsets()));
      result.setFont(JBFont.small().deriveFont(Font.BOLD));
    }
    return result;
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
    myValueLabel.setOpaque(false);
    panel.add(myValueLabel, BorderLayout.CENTER);

    myShortcutLabel = new JLabel();
    myShortcutLabel.setBorder(JBUI.Borders.empty(0,0,1,3));
    myShortcutLabel.setForeground(UIManager.getColor("MenuItem.acceleratorForeground"));
    myShortcutLabel.setOpaque(false);
    panel.add(myShortcutLabel, BorderLayout.EAST);

    myMnemonicLabel = new JLabel();
    //noinspection HardCodedStringLiteral
    Dimension preferredSize = new JLabel("W").getPreferredSize();
    Insets insets = JBUI.CurrentTheme.ActionsList.numberMnemonicInsets();
    JBInsets.addTo(preferredSize, insets);
    myMnemonicLabel.setPreferredSize(preferredSize);
    myMnemonicLabel.setBorder(new JBEmptyBorder(insets));
    myMnemonicLabel.setFont(JBUI.CurrentTheme.ActionsList.applyStylesForNumberMnemonic(myMnemonicLabel.getFont()));
    myMnemonicLabel.setVisible(false);

    myIconBar = createIconBar();

    return layoutComponent(panel);
  }

  @Override
  protected void createLabel() {
    super.createLabel();
    myIconLabel = new JLabel();
  }

  @Override
  protected JComponent layoutComponent(JComponent middleItemComponent) {
    myNextStepLabel = new JLabel();
    myNextStepLabel.setOpaque(false);

    JPanel left = new JPanel(new BorderLayout());
    left.add(middleItemComponent, BorderLayout.CENTER);

    JPanel right = new JPanel(new BorderLayout());
    int leftRightInset = (ListPopupImpl.NEXT_STEP_AREA_WIDTH - AllIcons.Icons.Ide.MenuArrow.getIconWidth()) / 2;
    right.add(myNextStepLabel, BorderLayout.CENTER);

    myNextStepButtonSeparator = createNextStepButtonSeparator();
    left.add(myNextStepButtonSeparator, BorderLayout.EAST);

    if (myIconBar != null) {
      left.add(myIconBar, BorderLayout.WEST);
    }

    JPanel result = new JPanel();
    result.setLayout(new GridBagLayout());
    result.setBorder(JBUI.Borders.empty());

    Insets insets = getDefaultItemComponentBorder().getBorderInsets(result);
    left.setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, 0));
    right.setBorder(JBUI.Borders.empty(insets.top, leftRightInset, insets.bottom, insets.right));

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
    result.add(right, gbc.next());

    myMainPane = left;
    myButtonPane = right;

    return result;
  }

  @Override
  protected void setComponentIcon(Icon icon, Icon disabledIcon) {
    if (myIconLabel == null) return;
    myIconLabel.setIcon(icon);
    myIconLabel.setDisabledIcon(disabledIcon);
  }

  @NotNull
  protected static JComponent createNextStepButtonSeparator() {
    SeparatorComponent separator = new SeparatorComponent(JBColor.namedColor("Menu.separatorColor", JBColor.lightGray), SeparatorOrientation.VERTICAL);
    separator.setHGap(0);
    separator.setVGap(2);
    return separator;
  }

  @Nullable
  @Override
  protected Icon getItemIcon(E value, boolean isSelected) {
    if (!Registry.is("ide.list.popup.separate.next.step.button")) return super.getItemIcon(value, isSelected);

    ListPopupStep<Object> step = myPopup.getListStep();
    return step.hasSubstep(value) && step.isFinal(value) && isNextStepButtonSelected(myPopup.getList())
           ? myDescriptor.getIconFor(value)
           : super.getItemIcon(value, isSelected);
  }

  @Override
  protected void customizeComponent(JList<? extends E> list, E value, boolean isSelected) {
    if (mySeparatorComponent.isVisible() && mySeparatorComponent instanceof GroupHeaderSeparator) {
      ((GroupHeaderSeparator)mySeparatorComponent).setHideLine(myCurrentIndex == 0);
    }

    ListPopupStep<Object> step = myPopup.getListStep();
    boolean isSelectable = step.isSelectable(value);
    myTextLabel.setEnabled(isSelectable);

    myMainPane.setOpaque(false);
    myButtonPane.setOpaque(false);
    myNextStepButtonSeparator.setVisible(false);

    boolean nextStepButtonSelected = false;
    if (step.hasSubstep(value)) {
      myNextStepLabel.setVisible(isSelectable);

      if (Registry.is("ide.list.popup.separate.next.step.button") && step.isFinal(value)) {
        myMainPane.setOpaque(true);
        myButtonPane.setOpaque(true);
        myComponent.setBackground(calcBackground(false, isSelected));

        nextStepButtonSelected = isNextStepButtonSelected(list);
        myMainPane.setBackground(calcBackground(isSelected && !nextStepButtonSelected, isSelected));
        myButtonPane.setBackground(calcBackground(isSelected && nextStepButtonSelected, isSelected));
        setForegroundSelected(myTextLabel, isSelected && !nextStepButtonSelected);
        myNextStepLabel.setIcon(isSelectable && isSelected && nextStepButtonSelected ? AllIcons.Icons.Ide.MenuArrowSelected : AllIcons.Icons.Ide.MenuArrow);
        myNextStepButtonSeparator.setVisible(!isSelected);
      }
      else {
        myNextStepLabel.setIcon(isSelectable && isSelected ? AllIcons.Icons.Ide.MenuArrowSelected : AllIcons.Icons.Ide.MenuArrow);
        myComponent.setBackground(calcBackground(isSelected && isSelectable, false));
        setForegroundSelected(myTextLabel, isSelected && isSelectable);
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

    if (myMnemonicLabel != null && value instanceof NumericMnemonicItem && ((NumericMnemonicItem)value).digitMnemonicsEnabled()) {
      Character mnemonic = ((NumericMnemonicItem)value).getMnemonicChar();
      myMnemonicLabel.setText(mnemonic != null ? String.valueOf(mnemonic) : "");
      myMnemonicLabel.setForeground(isSelected && isSelectable && !nextStepButtonSelected ? getSelectionForeground() : JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND);
      myMnemonicLabel.setVisible(true);
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
        String shortcutText = null;
        if (set != null) {
          Shortcut shortcut = ArrayUtil.getFirstElement(set.getShortcuts());
          if (shortcut != null) {
            shortcutText = KeymapUtil.getShortcutText(shortcut);
          }
        }
        if (shortcutText == null && value instanceof AnActionHolder) {
          AnAction action = ((AnActionHolder)value).getAction();
          if (action instanceof UserDataHolder) {
            shortcutText = ((UserDataHolder)action).getUserData(CUSTOM_KEY_STROKE_TEXT);
          }
        }
        if (shortcutText != null) myShortcutLabel.setText("     " + shortcutText);
      }
      myShortcutLabel.setForeground(isSelected && isSelectable && !nextStepButtonSelected
                                    ? UIManager.getColor("MenuItem.acceleratorSelectionForeground")
                                    : UIManager.getColor("MenuItem.acceleratorForeground"));
    }

    if (myValueLabel != null) {
      myValueLabel.setText(step instanceof ListPopupStepEx<?> ? ((ListPopupStepEx<E>)step).getValueFor(value) : null);
      boolean selected = isSelected && isSelectable && !nextStepButtonSelected;
      setForegroundSelected(myValueLabel, selected);
    }
  }

  protected JComponent createIconBar() {
    Box res = Box.createHorizontalBox();
    res.setBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap()));
    res.add(myIconLabel);
    res.add(myMnemonicLabel);

    return res;
  }

  private Color calcBackground(boolean selected, boolean hovered) {
    if (selected) return getSelectionBackground();
    if (hovered) return JBUI.CurrentTheme.Table.Hover.background(true);

    return getBackground();
  }

  protected boolean isNextStepButtonSelected(JList<?> list) {
    if (!Registry.is("ide.list.popup.separate.next.step.button")) return false;
    return list instanceof ListPopupImpl.NestedList && ((ListPopupImpl.NestedList)list).isNextStepButtonSelected();
  }
}
