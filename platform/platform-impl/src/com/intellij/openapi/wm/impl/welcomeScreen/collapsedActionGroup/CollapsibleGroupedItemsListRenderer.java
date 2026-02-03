// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Special renderer for a list with AnAction (simple actions and action groups)
 *  that contains two separate components for collapsible groups and simple actions. */
@ApiStatus.Internal
public class CollapsibleGroupedItemsListRenderer implements ListCellRenderer<AnAction> {

  /** ListCellRendererComponent for group headers, both collapsible and not. */
  private final CollapsibleGroupHeaderSeparator separator = new CollapsibleGroupHeaderSeparator();

  /** Renders simple AnAction items, which are actually only name and icon. */
  private final JLabel textLabel = new JLabel();

  /** ListCellRendererComponent for simple AnAction Items. SelectablePanel wraps textLabel to provide a selection effect. */
  private final JComponent panel = createMainPanel();

  public CollapsibleGroupedItemsListRenderer() {
    textLabel.setBorder(JBUI.Borders.empty(5, 0));
    textLabel.setIconTextGap(JBUI.CurrentTheme.ActionsList.elementIconGap());
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends AnAction> list,
                                                AnAction value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    if (value instanceof CollapsedActionGroup actionGroup) {
      return prepareCollapsibleSeparator(list, actionGroup);
    }
    else if (value instanceof ActionGroup) {
      return prepareTitleSeparator(value);
    }
    else {
      return prepareActionRenderer(list, value, isSelected);
    }
  }

  private JComponent createMainPanel() {
    if (ExperimentalUI.isNewUI()) {
      var panel = SelectablePanel.wrap(textLabel);
      PopupUtil.configListRendererFlexibleHeight(panel);
      return panel;
    }
    else {
      return JBUI.Panels.simplePanel(textLabel)
        .withBorder(new EmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding()));
    }
  }

  protected static void updateSelectedState(JComponent component, boolean selected, Color background) {
    if (ExperimentalUI.isNewUI() && component instanceof SelectablePanel selectablePanel) {
      selectablePanel.setSelectionColor(selected ? JBUI.CurrentTheme.List.background(true, true) : null);
      selectablePanel.setBackground(background);
    }
    else {
      UIUtil.setBackgroundRecursively(component, selected ? UIUtil.getListSelectionBackground(true) : background);
    }
  }

  private CollapsibleGroupHeaderSeparator prepareCollapsibleSeparator(JList<? extends AnAction> list, CollapsedActionGroup actionGroup) {
    var groupChildren = actionGroup.getChildren(ActionManager.getInstance());

    var listMode = (DefaultListModel<? extends AnAction>)list.getModel();
    var groupExpanded = ContainerUtil.exists(groupChildren, item -> listMode.contains(item));

    if (!groupExpanded) {
      int maxChildWidth = Integer.MIN_VALUE;
      for (AnAction childAction : groupChildren) {
        setLabelByAction(childAction);
        maxChildWidth = Math.max(maxChildWidth, panel.getPreferredSize().width);
      }

      Dimension preferredSize = separator.getPreferredSize();
      if (maxChildWidth != Integer.MIN_VALUE && maxChildWidth > preferredSize.width) {
        separator.setPreferredSize(new Dimension(maxChildWidth, preferredSize.height));
      }
    }

    separator.setCaption(actionGroup.getTemplateText());
    separator.setExpandedState(groupExpanded ?
                               CollapsibleGroupHeaderSeparator.GroupHeaderSeparatorState.EXPANDED :
                               CollapsibleGroupHeaderSeparator.GroupHeaderSeparatorState.COLLAPSED);
    return separator;
  }

  private CollapsibleGroupHeaderSeparator prepareTitleSeparator(AnAction value) {
    separator.setCaption(value.getTemplateText());
    separator.setExpandedState(CollapsibleGroupHeaderSeparator.GroupHeaderSeparatorState.NONE);
    return separator;
  }

  private JComponent prepareActionRenderer(JList<? extends AnAction> list, AnAction value, boolean isSelected) {
    setLabelByAction(value);
    updateSelectedState(panel, isSelected, list.getBackground());
    return panel;
  }

  private void setLabelByAction(@NotNull AnAction value) {
    textLabel.setText(value.getTemplateText());
    textLabel.setIcon(value.getTemplatePresentation().getIcon());
  }
}