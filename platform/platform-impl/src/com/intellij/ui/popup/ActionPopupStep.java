// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ObjectUtils;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.function.Supplier;

public class ActionPopupStep implements ListPopupStepEx<PopupFactoryImpl.ActionItem>,
                                        MnemonicNavigationFilter<PopupFactoryImpl.ActionItem>,
                                        SpeedSearchFilter<PopupFactoryImpl.ActionItem> {
  private final List<PopupFactoryImpl.ActionItem> myItems;
  private final String myTitle;
  private final Supplier<? extends DataContext> myContext;
  private final String myActionPlace;
  private final boolean myEnableMnemonics;
  private final PresentationFactory myPresentationFactory;
  private final int myDefaultOptionIndex;
  private final boolean myAutoSelectionEnabled;
  private final boolean myShowDisabledActions;
  private Runnable myFinalRunnable;
  private final Condition<? super AnAction> myPreselectActionCondition;

  public ActionPopupStep(@NotNull List<PopupFactoryImpl.ActionItem> items,
                         @PopupTitle String title,
                         @NotNull Supplier<? extends DataContext> context,
                         @Nullable String actionPlace,
                         boolean enableMnemonics,
                         @Nullable Condition<? super AnAction> preselectActionCondition,
                         boolean autoSelection,
                         boolean showDisabledActions,
                         @Nullable PresentationFactory presentationFactory) {
    myItems = items;
    myTitle = title;
    myContext = context;
    myActionPlace = ObjectUtils.notNull(actionPlace, ActionPlaces.POPUP);
    myEnableMnemonics = enableMnemonics;
    myPresentationFactory = presentationFactory;
    myDefaultOptionIndex = getDefaultOptionIndexFromSelectCondition(preselectActionCondition, items);
    myPreselectActionCondition = preselectActionCondition;
    myAutoSelectionEnabled = autoSelection;
    myShowDisabledActions = showDisabledActions;
  }

  private static int getDefaultOptionIndexFromSelectCondition(@Nullable Condition<? super AnAction> preselectActionCondition,
                                                              @NotNull List<? extends PopupFactoryImpl.ActionItem> items) {
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

  @NotNull
  public static ListPopupStep<PopupFactoryImpl.ActionItem> createActionsStep(@NotNull ActionGroup actionGroup,
                                                                             @NotNull DataContext dataContext,
                                                                             boolean showNumbers,
                                                                             boolean useAlphaAsNumbers,
                                                                             boolean showDisabledActions,
                                                                             @PopupTitle String title,
                                                                             boolean honorActionMnemonics,
                                                                             boolean autoSelectionEnabled,
                                                                             Supplier<? extends DataContext> contextSupplier,
                                                                             @Nullable String actionPlace,
                                                                             Condition<? super AnAction> preselectCondition,
                                                                             int defaultOptionIndex,
                                                                             @Nullable PresentationFactory presentationFactory) {
    List<PopupFactoryImpl.ActionItem> items = createActionItems(
      actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, actionPlace, presentationFactory);
    boolean enableMnemonics = showNumbers ||
                              honorActionMnemonics &&
                              items.stream().anyMatch(actionItem -> actionItem.getAction().getTemplatePresentation().getMnemonic() != 0);

    return new ActionPopupStep(
      items, title, contextSupplier, actionPlace, enableMnemonics,
      preselectCondition != null ? preselectCondition :
      action -> defaultOptionIndex >= 0 &&
                defaultOptionIndex < items.size() && items.get(defaultOptionIndex).getAction().equals(action),
      autoSelectionEnabled,
      showDisabledActions, presentationFactory);
  }

  @NotNull
  public static List<PopupFactoryImpl.ActionItem> createActionItems(@NotNull ActionGroup actionGroup,
                                                                    @NotNull DataContext dataContext,
                                                                    boolean showNumbers,
                                                                    boolean useAlphaAsNumbers,
                                                                    boolean showDisabledActions,
                                                                    boolean honorActionMnemonics,
                                                                    @Nullable String actionPlace,
                                                                    @Nullable PresentationFactory presentationFactory) {
    ActionStepBuilder builder = new ActionStepBuilder(
      dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, actionPlace, presentationFactory);
    builder.buildGroup(actionGroup);
    return builder.getItems();
  }

  @Override
  @NotNull
  public List<PopupFactoryImpl.ActionItem> getValues() {
    return myItems;
  }

  @Override
  public boolean isSelectable(final PopupFactoryImpl.ActionItem value) {
    return value.isEnabled();
  }

  @Override
  public int getMnemonicPos(final PopupFactoryImpl.ActionItem value) {
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
  public Icon getIconFor(final PopupFactoryImpl.ActionItem aValue) {
    return aValue.getIcon(false);
  }

  @Override
  public Icon getSelectedIconFor(PopupFactoryImpl.ActionItem value) {
    return value.getIcon(true);
  }

  @Override
  @NotNull
  public String getTextFor(final PopupFactoryImpl.ActionItem value) {
    return value.getText();
  }

  @Nullable
  @Override
  public String getTooltipTextFor(PopupFactoryImpl.ActionItem value) {
    return value.getDescription();
  }

  @Override
  public void setEmptyText(@NotNull StatusText emptyText) {
  }

  @Override
  public ListSeparator getSeparatorAbove(final PopupFactoryImpl.ActionItem value) {
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
  public PopupStep onChosen(final PopupFactoryImpl.ActionItem actionChoice, final boolean finalChoice) {
    return onChosen(actionChoice, finalChoice, 0);
  }

  @Override
  public PopupStep<PopupFactoryImpl.ActionItem> onChosen(PopupFactoryImpl.ActionItem actionChoice,
                                                         boolean finalChoice,
                                                         int eventModifiers) {
    if (!actionChoice.isEnabled()) return FINAL_CHOICE;
    final AnAction action = actionChoice.getAction();
    final DataContext dataContext = myContext.get();
    if (action instanceof ActionGroup && (!finalChoice || !((ActionGroup)action).canBePerformed(dataContext))) {
      return createActionsStep(
        (ActionGroup)action,
        dataContext,
        myEnableMnemonics,
        true,
        myShowDisabledActions,
        null,
        false, false,
        myContext,
        myActionPlace,
        myPreselectActionCondition, -1,
        myPresentationFactory);
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
    DataContext dataContext = myContext.get();
    AnActionEvent event = new AnActionEvent(
      inputEvent, dataContext, myActionPlace, action.getTemplatePresentation().clone(),
      ActionManager.getInstance(), modifiers);
    event.setInjectedContext(action.isInInjectedContext());
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event, dataContext);
    }
  }

  @Override
  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  @Override
  public boolean hasSubstep(final PopupFactoryImpl.ActionItem selectedValue) {
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
  public MnemonicNavigationFilter<PopupFactoryImpl.ActionItem> getMnemonicNavigationFilter() {
    return this;
  }

  @Override
  public String getIndexedString(final PopupFactoryImpl.ActionItem value) {
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
  public SpeedSearchFilter<PopupFactoryImpl.ActionItem> getSpeedSearchFilter() {
    return this;
  }
}
