// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SizedIcon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.actionSystem.Presentation.restoreTextWithMnemonic;

class ActionStepBuilder {
  private final List<PopupFactoryImpl.ActionItem> myListModel;
  private final DataContext myDataContext;
  private final boolean                         myShowNumbers;
  private final boolean                         myUseAlphaAsNumbers;
  private final PresentationFactory             myPresentationFactory;
  private final boolean                         myShowDisabled;
  private       int                             myCurrentNumber;
  private       boolean                         myPrependWithSeparator;
  private       String                          mySeparatorText;
  private final boolean                         myHonorActionMnemonics;
  private final String                          myActionPlace;
  private Icon myEmptyIcon;
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
      myPresentationFactory = new PresentationFactory();
    }
    else {
      myPresentationFactory = ObjectUtils.notNull(presentationFactory);
    }
    myListModel = new ArrayList<>();
    myDataContext = dataContext;
    myShowNumbers = showNumbers;
    myShowDisabled = showDisabled;
    myCurrentNumber = 0;
    myPrependWithSeparator = false;
    mySeparatorText = null;
    myHonorActionMnemonics = honorActionMnemonics;
    myActionPlace = ObjectUtils.notNull(actionPlace, ActionPlaces.UNKNOWN);
  }

  @NotNull
  public List<PopupFactoryImpl.ActionItem> getItems() {
    return myListModel;
  }

  public void buildGroup(@NotNull ActionGroup actionGroup) {
    calcMaxIconSize(actionGroup);
    myEmptyIcon = myMaxIconHeight != -1 && myMaxIconWidth != -1 ? EmptyIcon.create(myMaxIconWidth, myMaxIconHeight) : null;

    appendActionsFromGroup(actionGroup);

    if (myListModel.isEmpty()) {
      myListModel.add(new PopupFactoryImpl.ActionItem(Utils.EMPTY_MENU_FILLER, Utils.NOTHING_HERE, null, false, null, null, false, null));
    }
  }

  private void calcMaxIconSize(final ActionGroup actionGroup) {
    if (myPresentationFactory instanceof MenuItemPresentationFactory &&
        ((MenuItemPresentationFactory)myPresentationFactory).shallHideIcons()) return;
    AnAction[] actions = actionGroup.getChildren(createActionEvent(actionGroup));
    for (AnAction action : actions) {
      if (action == null) continue;
      if (action instanceof ActionGroup) {
        final ActionGroup group = (ActionGroup)action;
        if (!group.isPopup()) {
          calcMaxIconSize(group);
          continue;
        }
      }

      Icon icon = action.getTemplatePresentation().getIcon();
      if (icon == null && action instanceof Toggleable) icon = EmptyIcon.ICON_16;
      if (icon != null) {
        final int width = icon.getIconWidth();
        final int height = icon.getIconHeight();
        if (myMaxIconWidth < width) {
          myMaxIconWidth = width;
        }
        if (myMaxIconHeight < height) {
          myMaxIconHeight = height;
        }
      }
    }
  }

  @NotNull
  private AnActionEvent createActionEvent(@NotNull AnAction action) {
    AnActionEvent actionEvent = AnActionEvent.createFromDataContext(myActionPlace, myPresentationFactory.getPresentation(action), myDataContext);
    actionEvent.setInjectedContext(action.isInInjectedContext());
    return actionEvent;
  }

  private void appendActionsFromGroup(@NotNull ActionGroup actionGroup) {
    List<AnAction> newVisibleActions = Utils.expandActionGroup(false, actionGroup, myPresentationFactory, myDataContext, myActionPlace);
    for (AnAction action : newVisibleActions) {
      if (action instanceof Separator) {
        myPrependWithSeparator = true;
        mySeparatorText = ((Separator)action).getText();
      }
      else {
        appendAction(action);
      }
    }
  }

  private void appendAction(@NotNull AnAction action) {
    Presentation presentation = myPresentationFactory.getPresentation(action);
    boolean enabled = presentation.isEnabled();
    if ((myShowDisabled || enabled) && presentation.isVisible()) {
      String text = presentation.getText();
      if (myShowNumbers) {
        if (myCurrentNumber < 9) {
          text = "&" + (myCurrentNumber + 1) + ". " + text;
        }
        else if (myCurrentNumber == 9) {
          text = "&" + 0 + ". " + text;
        }
        else if (myUseAlphaAsNumbers) {
          text = "&" + (char)('A' + myCurrentNumber - 10) + ". " + text;
        }
        myCurrentNumber++;
      }
      else if (myHonorActionMnemonics) {
        text = restoreTextWithMnemonic(text, action.getTemplatePresentation().getMnemonic());
      }

      boolean hideIcon = Boolean.TRUE.equals(presentation.getClientProperty(MenuItemPresentationFactory.HIDE_ICON));
      Icon icon = hideIcon ? null : presentation.getIcon();
      Icon selectedIcon = hideIcon ? null : presentation.getSelectedIcon();
      Icon disabledIcon = hideIcon ? null : presentation.getDisabledIcon();

      if (icon == null && selectedIcon == null) {
        @NonNls final String actionId = ActionManager.getInstance().getId(action);
        if (actionId != null && actionId.startsWith("QuickList.")) {
          //icon =  null; // AllIcons.Actions.QuickList;
        }
        else if (action instanceof Toggleable && Toggleable.isSelected(presentation)) {
          icon = LafIconLookup.getIcon("checkmark");
          selectedIcon = LafIconLookup.getSelectedIcon("checkmark");
          disabledIcon = LafIconLookup.getDisabledIcon("checkmark");
        }
      }
      if (!enabled) {
        icon = disabledIcon != null || icon == null ? disabledIcon : IconLoader.getDisabledIcon(icon);
        selectedIcon = disabledIcon != null || selectedIcon == null ? disabledIcon : IconLoader.getDisabledIcon(selectedIcon);
      }

      if (myMaxIconWidth != -1 && myMaxIconHeight != -1) {
        if (icon != null) icon = new SizedIcon(icon, myMaxIconWidth, myMaxIconHeight);
        if (selectedIcon != null) selectedIcon = new SizedIcon(selectedIcon, myMaxIconWidth, myMaxIconHeight);
      }

      if (icon == null) icon = selectedIcon != null ? selectedIcon : myEmptyIcon;
      boolean prependSeparator = (!myListModel.isEmpty() || mySeparatorText != null) && myPrependWithSeparator;
      assert text != null : action + " has no presentation";
      myListModel.add(
        new PopupFactoryImpl.ActionItem(action, text, (String)presentation.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY),
                                        enabled, icon, selectedIcon, prependSeparator, mySeparatorText));
      myPrependWithSeparator = false;
      mySeparatorText = null;
    }
  }
}
