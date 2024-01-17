// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class ActionStepBuilder {
  private final List<PopupFactoryImpl.ActionItem> myListModel;
  private final DataContext myDataContext;
  private final boolean                         myShowNumbers;
  private final boolean                         myUseAlphaAsNumbers;
  private final PresentationFactory presentationFactory;
  private final boolean                         myShowDisabled;
  private       int                             myCurrentNumber;
  private       boolean                         myPrependWithSeparator;
  private @NlsContexts.Separator String mySeparatorText;
  private final boolean                         myHonorActionMnemonics;
  private final String                          myActionPlace;
  private int myMaxIconWidth  = -1;
  private int myMaxIconHeight = -1;

  ActionStepBuilder(@NotNull DataContext dataContext,
                    boolean showNumbers,
                    boolean useAlphaAsNumbers,
                    boolean showDisabled,
                    boolean honorActionMnemonics,
                    @Nullable String actionPlace,
                    @Nullable PresentationFactory presentationFactory) {
    myUseAlphaAsNumbers = useAlphaAsNumbers;
    if (presentationFactory == null) {
      this.presentationFactory = new PresentationFactory();
    }
    else {
      this.presentationFactory = Objects.requireNonNull(presentationFactory);
    }
    myListModel = new ArrayList<>();
    myDataContext = dataContext;
    myShowNumbers = showNumbers;
    myShowDisabled = showDisabled;
    myCurrentNumber = 0;
    myPrependWithSeparator = false;
    mySeparatorText = null;
    myHonorActionMnemonics = honorActionMnemonics;
    myActionPlace = ObjectUtils.notNull(actionPlace, ActionPlaces.POPUP);
  }

  public @NotNull List<PopupFactoryImpl.ActionItem> getItems() {
    return myListModel;
  }

  public void buildGroup(@NotNull ActionGroup actionGroup) {
    appendActionsFromGroup(actionGroup);
    if (myListModel.isEmpty()) {
      myListModel.add(new PopupFactoryImpl.ActionItem(
        Utils.EMPTY_MENU_FILLER,
        Objects.requireNonNull(Utils.EMPTY_MENU_FILLER.getTemplateText())));
    }
  }

  private void calcMaxIconSize(@NotNull List<? extends AnAction> actions) {
    for (AnAction action : actions) {
      if (action instanceof Separator) {
        continue;
      }

      Presentation presentation = presentationFactory.getPresentation(action);
      Pair<Icon, Icon> icons = calcRawIcons(action, presentation, true);
      Icon icon = icons.first == null ? icons.second : icons.first;
      if (icon == null) {
        continue;
      }

      int width = icon.getIconWidth();
      int height = icon.getIconHeight();
      if (myMaxIconWidth < width) {
        myMaxIconWidth = width;
      }
      if (myMaxIconHeight < height) {
        myMaxIconHeight = height;
      }
    }
  }

  private void appendActionsFromGroup(@NotNull ActionGroup actionGroup) {
    boolean multiChoicePopup = Utils.isMultiChoiceGroup(actionGroup);
    List<AnAction> newVisibleActions = Utils.expandActionGroup(
      actionGroup, presentationFactory, myDataContext, myActionPlace);
    List<AnAction> filtered = myShowDisabled ? newVisibleActions : ContainerUtil.filter(
      newVisibleActions, o -> o instanceof Separator || presentationFactory.getPresentation(o).isEnabled());
    calcMaxIconSize(filtered);
    for (AnAction action : filtered) {
      if (action instanceof Separator) {
        myPrependWithSeparator = true;
        mySeparatorText = ((Separator)action).getText();
      }
      else {
        Presentation presentation = presentationFactory.getPresentation(action);
        if (multiChoicePopup && action instanceof Toggleable) {
          presentation.setMultiChoice(true);
        }
        appendAction(action, presentation);
      }
    }
  }

  private void appendAction(@NotNull AnAction action, @NotNull Presentation presentation) {
    Character mnemonic = null;
    if (myShowNumbers) {
      if (myCurrentNumber < 9) {
        mnemonic = Character.forDigit(myCurrentNumber + 1, 10);
      }
      else if (myCurrentNumber == 9) {
        mnemonic = '0';
      }
      else if (myUseAlphaAsNumbers) {
        mnemonic = (char)('A' + myCurrentNumber - 10);
      }
      myCurrentNumber++;
    }

    boolean prependSeparator = (!myListModel.isEmpty() || mySeparatorText != null) && myPrependWithSeparator;
    List<PopupFactoryImpl.InlineActionItem> inlineItems = createInlineActionsItems(action, presentation);
    PopupFactoryImpl.ActionItem actionItem = new PopupFactoryImpl.ActionItem(
      action, mnemonic, myShowNumbers, myHonorActionMnemonics,
      myMaxIconWidth, myMaxIconHeight, prependSeparator, mySeparatorText, inlineItems);
    actionItem.updateFromPresentation(presentation, myActionPlace);
    myListModel.add(actionItem);
    myPrependWithSeparator = false;
    mySeparatorText = null;
  }

  private @NotNull List<PopupFactoryImpl.InlineActionItem> createInlineActionsItems(@NotNull AnAction action,
                                                                                    @NotNull Presentation presentation) {
    List<? extends AnAction> inlineActions = presentation.getClientProperty(ActionUtil.INLINE_ACTIONS);
    if (inlineActions == null && action instanceof InlineActionsHolder holder) inlineActions = holder.getInlineActions();
    if (inlineActions == null) return Collections.emptyList();
    List<PopupFactoryImpl.InlineActionItem> res = new ArrayList<>();
    for (AnAction a : inlineActions) {
      Presentation p = presentationFactory.getPresentation(a);
      if (!p.isVisible()) continue;
      PopupFactoryImpl.InlineActionItem item = new PopupFactoryImpl.InlineActionItem(a, myMaxIconWidth, myMaxIconHeight);
      item.updateFromPresentation(p, myActionPlace);
      res.add(item);
    }
    return res.isEmpty() ? Collections.emptyList() : res;
  }

  static @NotNull Pair<Icon, Icon> calcRawIcons(@NotNull AnAction action, @NotNull Presentation presentation, boolean forceChecked) {
    boolean hideIcon = Boolean.TRUE.equals(presentation.getClientProperty(MenuItemPresentationFactory.HIDE_ICON));
    Icon icon = hideIcon ? null : presentation.getIcon();
    Icon selectedIcon = hideIcon ? null : presentation.getSelectedIcon();
    Icon disabledIcon = hideIcon ? null : presentation.getDisabledIcon();

    if (icon == null && selectedIcon == null) {
      String actionId = ActionManager.getInstance().getId(action);
      if (actionId != null && actionId.startsWith("QuickList.")) {
        //icon =  null; // AllIcons.Actions.QuickList;
      }
      else if (action instanceof Toggleable && (Toggleable.isSelected(presentation) || forceChecked)) {
        icon = LafIconLookup.getIcon("checkmark");
        selectedIcon = LafIconLookup.getSelectedIcon("checkmark");
        disabledIcon = LafIconLookup.getDisabledIcon("checkmark");
      }
    }
    if (!presentation.isEnabled()) {
      icon = disabledIcon != null || icon == null ? disabledIcon : IconLoader.getDisabledIcon(icon);
      selectedIcon = disabledIcon != null || selectedIcon == null ? disabledIcon : IconLoader.getDisabledIcon(selectedIcon);
    }
    return new Pair<>(icon, selectedIcon);
  }
}
