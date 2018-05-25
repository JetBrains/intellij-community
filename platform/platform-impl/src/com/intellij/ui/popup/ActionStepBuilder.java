// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SizedIcon;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.actionSystem.Presentation.restoreTextWithMnemonic;

public class ActionStepBuilder extends PresentationFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupFactoryImpl");

  private final List<PopupFactoryImpl.ActionItem> myListModel;
  private final DataContext myDataContext;
  private final boolean                         myShowNumbers;
  private final boolean                         myUseAlphaAsNumbers;
  private final boolean                         myShowDisabled;
  private       int                             myCurrentNumber;
  private       boolean                         myPrependWithSeparator;
  private       String                          mySeparatorText;
  private final boolean                         myHonorActionMnemonics;
  private Icon myEmptyIcon;
  private int myMaxIconWidth  = -1;
  private int myMaxIconHeight = -1;
  @NotNull private String myActionPlace;

  public ActionStepBuilder(@NotNull DataContext dataContext,
                           final boolean showNumbers,
                           final boolean useAlphaAsNumbers,
                           final boolean showDisabled,
                           final boolean honorActionMnemonics)
  {
    myUseAlphaAsNumbers = useAlphaAsNumbers;
    myListModel = new ArrayList<>();
    myDataContext = dataContext;
    myShowNumbers = showNumbers;
    myShowDisabled = showDisabled;
    myCurrentNumber = 0;
    myPrependWithSeparator = false;
    mySeparatorText = null;
    myHonorActionMnemonics = honorActionMnemonics;
    myActionPlace = ActionPlaces.UNKNOWN;
  }

  public void setActionPlace(@NotNull String actionPlace) {
    myActionPlace = actionPlace;
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
  private AnActionEvent createActionEvent(@NotNull AnAction actionGroup) {
    final AnActionEvent actionEvent =
      new AnActionEvent(null, myDataContext, myActionPlace, getPresentation(actionGroup), ActionManager.getInstance(), 0);
    actionEvent.setInjectedContext(actionGroup.isInInjectedContext());
    return actionEvent;
  }

  private void appendActionsFromGroup(@NotNull ActionGroup actionGroup) {
    List<AnAction> newVisibleActions = ContainerUtil.newArrayListWithCapacity(100);
    Utils.expandActionGroup(false, actionGroup, newVisibleActions, this, myDataContext, myActionPlace, ActionManager.getInstance());
    for (AnAction action : newVisibleActions) {
      if (action == null) {
        LOG.error("null action in group " + actionGroup);
        continue;
      }
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
    Presentation presentation = getPresentation(action);
    AnActionEvent event = createActionEvent(action);

    ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, event, true);
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

      Icon icon = presentation.getIcon();
      Icon selectedIcon = presentation.getSelectedIcon();
      Icon disabledIcon = presentation.getDisabledIcon();

      if (icon == null && selectedIcon == null) {
        @NonNls final String actionId = ActionManager.getInstance().getId(action);
        if (actionId != null && actionId.startsWith("QuickList.")) {
          icon =  AllIcons.Actions.QuickList;
        }
        else if (action instanceof Toggleable && Boolean.TRUE.equals(presentation.getClientProperty(Toggleable.SELECTED_PROPERTY))) {
          icon = LafIconLookup.getIcon("checkmark");
          selectedIcon = LafIconLookup.getSelectedIcon("checkmark");
          disabledIcon = LafIconLookup.getDisabledIcon("checkmark");
        }
      }
      if (!enabled) {
        icon = disabledIcon != null ? disabledIcon : IconLoader.getDisabledIcon(icon);
        selectedIcon = disabledIcon != null ? disabledIcon : IconLoader.getDisabledIcon(selectedIcon);
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
