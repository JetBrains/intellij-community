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
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.*;
import com.intellij.ui.popup.NumericMnemonicItem;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collections;

public class PopupListElementRenderer<E> extends GroupedItemsListRenderer<E> {

  public static final Key<@NlsSafe String> CUSTOM_KEY_STROKE_TEXT = new Key<>("CUSTOM_KEY_STROKE_TEXT");
  protected final ListPopupImpl myPopup;
  private JLabel myShortcutLabel;
  private @Nullable JLabel myValueLabel;
  protected JLabel myMnemonicLabel;
  protected JLabel myIconLabel;

  protected JPanel myButtonPane;
  protected JComponent myMainPane;
  protected JComponent myButtonsSeparator;
  protected JComponent myIconBar;

  private final PopupInlineActionsSupport myInlineActionsSupport;

  private UpdateScaleHelper myUpdateScaleHelper = new UpdateScaleHelper();

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
    myInlineActionsSupport = PopupInlineActionsSupport.Companion.create(myPopup);
  }

  public ListPopupImpl getPopup() {
    return myPopup;
  }

  @Override
  protected SeparatorWithText createSeparator() {
    Insets labelInsets = ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.separatorLabelInsets() :
                         getDefaultItemComponentBorder().getBorderInsets(new JLabel());
    return new GroupHeaderSeparator(labelInsets);
  }

  @Override
  protected Color getBackground() {
    return ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.BACKGROUND : super.getBackground();
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

      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (ExperimentalUI.isNewUI()) {
          size.height = JBUI.CurrentTheme.List.rowHeight();
        }
        return size;
      }
    };
    panel.add(myTextLabel, BorderLayout.WEST);

    myValueLabel = new JLabel();
    myValueLabel.setEnabled(false);
    JBEmptyBorder valueBorder = ExperimentalUI.isNewUI() ? JBUI.Borders.empty() : JBUI.Borders.empty(0, 8, 1, 0);
    myValueLabel.setBorder(valueBorder);
    myValueLabel.setForeground(UIManager.getColor("MenuItem.acceleratorForeground"));
    panel.add(myValueLabel, BorderLayout.CENTER);

    myShortcutLabel = new JLabel();
    JBEmptyBorder shortcutBorder = ExperimentalUI.isNewUI() ? JBUI.Borders.empty() : JBUI.Borders.empty(0,0,1,3);
    myShortcutLabel.setBorder(shortcutBorder);
    myShortcutLabel.setForeground(UIManager.getColor("MenuItem.acceleratorForeground"));
    panel.add(myShortcutLabel, BorderLayout.EAST);

    myMnemonicLabel = new JLabel();
    myMnemonicLabel.setFont(JBUI.CurrentTheme.ActionsList.applyStylesForNumberMnemonic(myMnemonicLabel.getFont()));

    if (!ExperimentalUI.isNewUI()) {
      Insets insets = JBUI.CurrentTheme.ActionsList.numberMnemonicInsets();
      myMnemonicLabel.setBorder(new JBEmptyBorder(insets));
      //noinspection HardCodedStringLiteral
      Dimension preferredSize = new JLabel("W").getPreferredSize();
      JBInsets.addTo(preferredSize, insets);
      myMnemonicLabel.setPreferredSize(preferredSize);
    }
    else {
      myMnemonicLabel.setBorder(new JBEmptyBorder(JBUI.CurrentTheme.ActionsList.mnemonicInsets()));
      myMnemonicLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      //noinspection HardCodedStringLiteral
      myMnemonicLabel.setText("W");
      Dimension preferredSize = myMnemonicLabel.getPreferredSize();
      myMnemonicLabel.setText(null);
      JBInsets.addTo(preferredSize, JBUI.insetsLeft(4));
      myMnemonicLabel.setPreferredSize(preferredSize);
      myMnemonicLabel.setMinimumSize(JBUI.size(12, myMnemonicLabel.getMinimumSize().height));
    }

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

    JPanel left = new JPanel(new BorderLayout());
    left.add(middleItemComponent, BorderLayout.CENTER);

    JPanel right = new JPanel(new GridBagLayout());

    myButtonsSeparator = createButtonsSeparator();
    left.add(myButtonsSeparator, BorderLayout.EAST);

    if (myIconBar != null) {
      left.add(myIconBar, BorderLayout.WEST);
    }

    JPanel result;
    if (ExperimentalUI.isNewUI()) {
      result = new SelectablePanel();
      result.setOpaque(false);
      PopupUtil.configListRendererFixedHeight(((SelectablePanel)result));
    }
    else {
      result = new JPanel();
      result.setBorder(JBUI.Borders.empty());
    }
    result.setLayout(new GridBagLayout());

    Insets insets = getDefaultItemComponentBorder().getBorderInsets(result);
    if (ExperimentalUI.isNewUI()) {
      left.setBorder(JBUI.Borders.empty());
      right.setBorder(JBUI.Borders.empty());
    } else {
      int leftRightInset = (ListPopupImpl.NEXT_STEP_AREA_WIDTH - AllIcons.Icons.Ide.MenuArrow.getIconWidth()) / 2;
      left.setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, 0));
      right.setBorder(JBUI.Borders.empty(insets.top, leftRightInset, insets.bottom, insets.right));
    }

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
    if (ExperimentalUI.isNewUI() && icon != null && icon.getIconWidth() != -1 && icon.getIconHeight() != -1) {
      myIconLabel.setBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap() - 2));
    }
  }

  @NotNull
  protected static JComponent createButtonsSeparator() {
    SeparatorComponent separator = new SeparatorComponent(JBUI.CurrentTheme.List.buttonSeparatorColor(), SeparatorOrientation.VERTICAL);
    separator.setHGap(1);
    separator.setVGap(JBUI.CurrentTheme.List.buttonSeparatorInset());
    return separator;
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


    boolean nextStepButtonSelected = false;
    boolean showNextStepLabel = step.hasSubstep(value) && !myInlineActionsSupport.hasExtraButtons(value);
    if (showNextStepLabel) {
      myNextStepLabel.setVisible(isSelectable);
      myNextStepLabel.setIcon(isSelectable && isSelected ? AllIcons.Icons.Ide.MenuArrowSelected : AllIcons.Icons.Ide.MenuArrow);
      if (ExperimentalUI.isNewUI()) {
        myNextStepLabel.setBorder(JBUI.Borders.emptyLeft(20));
      }
      else {
        getItemComponent().setBackground(calcBackground(isSelected && isSelectable));
      }
      setForegroundSelected(myTextLabel, isSelected && isSelectable);
    }
    else {
      myNextStepLabel.setVisible(false);
    }

    boolean hasNextIcon = myNextStepLabel.isVisible();
    boolean hasInlineButtons = updateExtraButtons(list, value, step, isSelected, hasNextIcon);

    if (ExperimentalUI.isNewUI() && getItemComponent() instanceof SelectablePanel selectablePanel) {
      myUpdateScaleHelper.saveScaleAndRunIfChanged(() -> {
        if (ExperimentalUI.isNewUI()) {
          PopupUtil.configListRendererFixedHeight(selectablePanel);
        }
      });

      selectablePanel.setSelectionColor(isSelected && isSelectable ? UIUtil.getListSelectionBackground(true) : null);

      int leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get();
      Insets innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets();
      //noinspection UseDPIAwareBorders
      selectablePanel.setBorder(
        new EmptyBorder(0, innerInsets.left + leftRightInset, 0, hasNextIcon || hasInlineButtons ? leftRightInset : leftRightInset + leftRightInset));
    }

    if (step instanceof BaseListPopupStep) {
      Color bg = ((BaseListPopupStep<E>)step).getBackgroundFor(value);
      Color fg = ((BaseListPopupStep<E>)step).getForegroundFor(value);
      if (!isSelected && fg != null) myTextLabel.setForeground(fg);
      if (!isSelected && bg != null) UIUtil.setBackgroundRecursively(getItemComponent(), bg);
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
      Color foreground =
        ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.mnemonicForeground() : JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND;
      myMnemonicLabel.setForeground(isSelected && isSelectable && !nextStepButtonSelected ? getSelectionForeground() : foreground);
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
        if (shortcutText != null) {
          myShortcutLabel.setText("     " + shortcutText);
          if (ExperimentalUI.isNewUI()) {
            myNextStepLabel.setBorder(JBUI.Borders.emptyLeft(6));
          }
        }
      }
      myShortcutLabel.setForeground(isSelected && isSelectable && !nextStepButtonSelected
                                    ? UIManager.getColor("MenuItem.acceleratorSelectionForeground")
                                    : UIManager.getColor("MenuItem.acceleratorForeground"));
    }

    if (myValueLabel != null) {
      String valueLabelText = step instanceof ListPopupStepEx<?> ? ((ListPopupStepEx<E>)step).getValueFor(value) : null;
      myValueLabel.setText(valueLabelText);
      if (ExperimentalUI.isNewUI()) {
        myValueLabel.setBorder(JBUI.Borders.emptyLeft(Strings.isEmpty(valueLabelText) ? 0 : 6));
      }
      boolean selected = isSelected && isSelectable && !nextStepButtonSelected;
      setForegroundSelected(myValueLabel, selected);
    }

    if (ExperimentalUI.isNewUI() && getItemComponent() instanceof SelectablePanel selectablePanel) {
      selectablePanel.setSelectionColor(isSelected && isSelectable ? UIUtil.getListSelectionBackground(true) : null);
      setSelected(myMainPane, isSelected && isSelectable);
    }
  }

  private boolean updateExtraButtons(JList<? extends E> list, E value, ListPopupStep<Object> step, boolean isSelected, boolean hasNextIcon) {
    myButtonPane.removeAll();
    GridBag gb = new GridBag().setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultAnchor(GridBagConstraints.CENTER)
      .setDefaultWeightX(1.0)
      .setDefaultWeightY(1.0);

    boolean isSelectable = step.isSelectable(value);
    java.util.List<JComponent> extraButtons;
    if (!isSelected || !isSelectable) {
      extraButtons = Collections.emptyList();
    } else {
      extraButtons = myInlineActionsSupport.getExtraButtons(list, value, true);
    }

    if (!extraButtons.isEmpty()) {
      myButtonsSeparator.setVisible(true);
      extraButtons.forEach(comp -> myButtonPane.add(comp, gb.next()));
      Integer activeButtonIndex = myInlineActionsSupport.getActiveButtonIndex(list);
      // We ONLY need to update the tooltip if there's an active inline action button.
      // Otherwise, it's set earlier from the main action.
      // If there is an active button without a tooltip, we still need to set the tooltip
      // to null, otherwise it'll look ugly, as if the inline action button has the same
      // tooltip as the main action.
      if (activeButtonIndex != null) {
        myRendererComponent.setToolTipText(myInlineActionsSupport.getActiveExtraButtonToolTipText(list, value));
      }
    }
    else if (!hasNextIcon && myInlineActionsSupport.hasExtraButtons(value)){
      myButtonsSeparator.setVisible(false);
      myButtonPane.add(Box.createHorizontalStrut(InlineActionsUtilKt.buttonWidth()), gb.next());
    }
    else {
      myButtonsSeparator.setVisible(false);
      myButtonPane.add(myNextStepLabel, gb.next());
    }

    return !extraButtons.isEmpty();
  }

  protected JComponent createIconBar() {
    Box res = Box.createHorizontalBox();
    res.add(myIconLabel);

    if (!ExperimentalUI.isNewUI()) {
      res.setBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap()));
      res.add(myMnemonicLabel);
    } else {
      //need to wrap to align mnemonics to the right
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(myMnemonicLabel);
      res.add(wrapper);
    }

    return res;
  }

  private Color calcBackground(boolean selected) {
    return selected ? getSelectionBackground() : getBackground();
  }

  @NotNull
  static Insets getListCellPadding() {
    if (ExperimentalUI.isNewUI()) {
      int leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get();
      return JBUI.insets(0, leftRightInset, 0, leftRightInset);
    }

    return UIUtil.getListCellPadding();
  }
}
