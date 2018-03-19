// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.actionSystem.Presentation.PROP_TEXT;

public class ActionPopupStep implements ListPopupStepEx<ActionPopupStep.ActionItem>, MnemonicNavigationFilter<ActionPopupStep.ActionItem>,
                                        SpeedSearchFilter<ActionPopupStep.ActionItem> {
  private final List<ActionItem> myItems;
  private final String myTitle;
  private final Supplier<DataContext> myContext;
  private final boolean myEnableMnemonics;
  private final int myDefaultOptionIndex;
  private final boolean myAutoSelectionEnabled;
  private final boolean myShowDisabledActions;
  private Runnable myFinalRunnable;
  @Nullable private final Condition<AnAction> myPreselectActionCondition;

  public ActionPopupStep(@NotNull final List<ActionItem> items,
                         final String title,
                         @NotNull Supplier<DataContext> context,
                         boolean enableMnemonics,
                         @Nullable Condition<AnAction> preselectActionCondition,
                         final boolean autoSelection,
                         boolean showDisabledActions) {
    myItems = items;
    myTitle = title;
    myContext = context;
    myEnableMnemonics = enableMnemonics;
    myDefaultOptionIndex = getDefaultOptionIndexFromSelectCondition(preselectActionCondition, items);
    myPreselectActionCondition = preselectActionCondition;
    myAutoSelectionEnabled = autoSelection;
    myShowDisabledActions = showDisabledActions;
  }

  private static int getDefaultOptionIndexFromSelectCondition(@Nullable Condition<AnAction> preselectActionCondition,
                                                              @NotNull List<ActionItem> items) {
    int defaultOptionIndex = 0;
    if (preselectActionCondition != null) {
      for (int i = 0; i < items.size(); i++) {
        final AnAction action = items.get(i).getAction();
        if (preselectActionCondition.value(action)) {
          defaultOptionIndex = i;
          break;
        }
      }
    }
    return defaultOptionIndex;
  }

  public static ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup,
                                                @NotNull DataContext dataContext,
                                                boolean showNumbers,
                                                boolean useAlphaAsNumbers,
                                                boolean showDisabledActions,
                                                String title,
                                                boolean honorActionMnemonics, final boolean autoSelectionEnabled,
                                                Supplier<DataContext> contextSupplier,
                                                Condition<AnAction> preselectCondition,
                                                int defaultOptionIndex) {
    final ActionStepBuilder builder =
      new ActionStepBuilder(dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics);
    builder.buildGroup(actionGroup);
    final List<ActionItem> items = builder.getItems();
    boolean enableMnemonics = showNumbers ||
                              honorActionMnemonics &&
                              items.stream().anyMatch(actionItem -> actionItem.getAction().getTemplatePresentation().getMnemonic() != 0);

    return new ActionPopupStep(items,
                               title,
                               contextSupplier,
                               enableMnemonics,
                               preselectCondition != null ? preselectCondition : action -> defaultOptionIndex >= 0 &&
                                                                                          defaultOptionIndex < items.size() &&
                                                                                          items.get(defaultOptionIndex).getAction().equals(action),
                               autoSelectionEnabled,
                               showDisabledActions);
  }

  @Override
  @NotNull
  public List<ActionItem> getValues() {
    return myItems;
  }

  @Override
  public boolean isSelectable(final ActionItem value) {
    return value.isEnabled();
  }

  @Override
  public int getMnemonicPos(final ActionItem value) {
    final String text = getTextFor(value);
    int i = text.indexOf(UIUtil.MNEMONIC);
    if (i < 0) {
      i = text.indexOf('&');
    }
    if (i < 0) {
      i = text.indexOf('_');
    }
    return i;
  }

  @Override
  public Icon getIconFor(final ActionItem aValue) {
    return aValue.getIcon(false);
  }

  @Override
  public Icon getSelectedIconFor(ActionItem value) {
    return value.getIcon(true);
  }

  @Override
  @NotNull
  public String getTextFor(final ActionItem value) {
    return value.getText();
  }

  @Nullable
  @Override
  public String getTooltipTextFor(ActionItem value) {
    return value.getDescription();
  }

  @Override
  public void setEmptyText(@NotNull StatusText emptyText) {
  }

  @Override
  public ListSeparator getSeparatorAbove(final ActionItem value) {
    return value.isPrependWithSeparator() ? new ListSeparator(value.getSeparatorText()) : null;
  }

  @Override
  public int getDefaultOptionIndex() {
    return myDefaultOptionIndex;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public PopupStep onChosen(final ActionItem actionChoice, final boolean finalChoice) {
    return onChosen(actionChoice, finalChoice, 0);
  }

  @Override
  public PopupStep onChosen(ActionItem actionChoice, boolean finalChoice, final int eventModifiers) {
    if (!actionChoice.isEnabled()) return FINAL_CHOICE;
    final AnAction action = actionChoice.getAction();
    final DataContext dataContext = myContext.get();
    if (action instanceof ActionGroup && (!finalChoice || !((ActionGroup)action).canBePerformed(dataContext))) {
      return
        createActionsStep((ActionGroup)action,
                          dataContext,
                          myEnableMnemonics,
                          true,
                          myShowDisabledActions,
                          null,
                          false, false,
                          myContext,
                          myPreselectActionCondition, -1);
    }
    else {
      myFinalRunnable = () -> performAction(action, eventModifiers);
      return FINAL_CHOICE;
    }
  }

  public void performAction(@NotNull AnAction action, int modifiers) {
    performAction(action, modifiers, null);
  }

  public void performAction(@NotNull AnAction action, int modifiers, InputEvent inputEvent) {
    final DataContext dataContext = myContext.get();
    final AnActionEvent event = new AnActionEvent(inputEvent, dataContext, ActionPlaces.UNKNOWN, action.getTemplatePresentation().clone(),
                                                  ActionManager.getInstance(), modifiers);
    event.setInjectedContext(action.isInInjectedContext());
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAware(action, event);
    }
  }

  @Override
  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  @Override
  public boolean hasSubstep(final ActionItem selectedValue) {
    return selectedValue != null && selectedValue.isEnabled() && selectedValue.getAction() instanceof ActionGroup;
  }

  @Override
  public void canceled() {
  }

  @Override
  public boolean isMnemonicsNavigationEnabled() {
    return myEnableMnemonics;
  }

  @Override
  public MnemonicNavigationFilter<ActionItem> getMnemonicNavigationFilter() {
    return this;
  }

  @Override
  public boolean canBeHidden(final ActionItem value) {
    return true;
  }

  @Override
  public String getIndexedString(final ActionItem value) {
    return getTextFor(value);
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public boolean isAutoSelectionEnabled() {
    return myAutoSelectionEnabled;
  }

  @Override
  public SpeedSearchFilter<ActionItem> getSpeedSearchFilter() {
    return this;
  }

  public static class ActionItem implements ShortcutProvider {
    private final AnAction myAction;
    private String myText;
    private final boolean myIsEnabled;
    private final Icon myIcon;
    private final Icon mySelectedIcon;
    private final boolean myPrependWithSeparator;
    private final String mySeparatorText;
    private final String myDescription;

    ActionItem(@NotNull AnAction action,
               @NotNull String text,
               @Nullable String description,
               boolean enabled,
               @Nullable Icon icon,
               @Nullable Icon selectedIcon,
               final boolean prependWithSeparator,
               String separatorText) {
      myAction = action;
      myText = text;
      myIsEnabled = enabled;
      myIcon = icon;
      mySelectedIcon = selectedIcon;
      myPrependWithSeparator = prependWithSeparator;
      mySeparatorText = separatorText;
      myDescription = description;
      myAction.getTemplatePresentation().addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName() == PROP_TEXT) {
            myText = myAction.getTemplatePresentation().getText();
          }
        }
      });
    }

    @NotNull
    public AnAction getAction() {
      return myAction;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @Nullable
    public Icon getIcon(boolean selected) {
      return selected && mySelectedIcon != null ? mySelectedIcon : myIcon;
    }

    public boolean isPrependWithSeparator() {
      return myPrependWithSeparator;
    }

    public String getSeparatorText() {
      return mySeparatorText;
    }

    public boolean isEnabled() { return myIsEnabled; }

    public String getDescription() {
      return myDescription;
    }

    @Nullable
    @Override
    public ShortcutSet getShortcut() {
      return myAction.getShortcutSet();
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
