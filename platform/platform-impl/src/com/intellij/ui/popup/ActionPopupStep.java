// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ActionPopupStep implements ListPopupStepEx<PopupFactoryImpl.ActionItem>,
                                        MnemonicNavigationFilter<PopupFactoryImpl.ActionItem>,
                                        SpeedSearchFilter<PopupFactoryImpl.ActionItem> {
  private static final Logger LOG = Logger.getInstance(ActionPopupStep.class);

  private final @NotNull List<PopupFactoryImpl.ActionItem> myItems;
  private final @NlsContexts.PopupTitle @Nullable String myTitle;
  private final @NotNull Supplier<? extends DataContext> myContext;
  private final @NotNull String myActionPlace;
  private final boolean myEnableMnemonics;
  private final @NotNull PresentationFactory myPresentationFactory;
  private final int myDefaultOptionIndex;
  private final boolean myAutoSelectionEnabled;
  private final boolean myShowDisabledActions;
  private Runnable myFinalRunnable;
  private final Condition<? super AnAction> myPreselectActionCondition;
  private @NotNull BiFunction<DataContext, AnAction, DataContext> mySubStepContextAdjuster = (c, a) -> c;

  /** @deprecated {@link #ActionPopupStep(List, String, Supplier, String, PresentationFactory, boolean, Condition, boolean, boolean)}*/
  @Deprecated(forRemoval = true)
  public ActionPopupStep(@NotNull List<PopupFactoryImpl.ActionItem> items,
                         @PopupTitle @Nullable String title,
                         @NotNull Supplier<? extends DataContext> context,
                         @Nullable String actionPlace,
                         boolean enableMnemonics,
                         @Nullable Condition<? super AnAction> preselectCondition,
                         boolean autoSelection,
                         boolean showDisabledActions,
                         @Nullable PresentationFactory presentationFactory) {
    this(items, title, context, actionPlace == null ? ActionPlaces.POPUP : actionPlace,
         presentationFactory == null ? new PresentationFactory() : presentationFactory,
         ActionPopupOptions.forStep(showDisabledActions, enableMnemonics, autoSelection, preselectCondition));
  }

  public ActionPopupStep(@NotNull List<PopupFactoryImpl.ActionItem> items,
                         @PopupTitle @Nullable String title,
                         @NotNull Supplier<? extends DataContext> context,
                         @NotNull String actionPlace,
                         @NotNull PresentationFactory presentationFactory,
                         @NotNull ActionPopupOptions options) {
    myItems = items;
    myTitle = title;
    myContext = context;
    myActionPlace = getPopupOrMainMenuPlace(actionPlace);
    myEnableMnemonics = options.honorActionMnemonics;
    myPresentationFactory = presentationFactory;
    myDefaultOptionIndex = getDefaultOptionIndexFromSelectCondition(options.preselectCondition, items);
    myPreselectActionCondition = options.preselectCondition;
    myAutoSelectionEnabled = options.autoSelection;
    myShowDisabledActions = options.showDisabledActions;
    if (!isPopupOrMainMenuPlace(actionPlace)) {
      LOG.error("isPopupOrMainMenuPlace(" + actionPlace + ")==false. Use ActionPlaces.getPopupPlace.");
    }
  }

  public @NotNull PresentationFactory getPresentationFactory() {
    return myPresentationFactory;
  }

  public @NotNull String getActionPlace() {
    return myActionPlace;
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

  public static @NotNull ListPopupStep<PopupFactoryImpl.ActionItem> createActionsStep(@PopupTitle @Nullable String title,
                                                                                      @NotNull ActionGroup actionGroup,
                                                                                      @NotNull DataContext dataContext,
                                                                                      @NotNull String actionPlace,
                                                                                      @NotNull PresentationFactory presentationFactory,
                                                                                      @NotNull Supplier<? extends DataContext> contextSupplier,
                                                                                      @NotNull ActionPopupOptions options) {
    List<PopupFactoryImpl.ActionItem> items = createActionItems(
      actionGroup, dataContext, actionPlace, presentationFactory, options);

    ActionPopupOptions stepOptions = ActionPopupOptions.forStep(
      options.showDisabledActions, options.showNumbers || options.honorActionMnemonics && PopupFactoryImpl.anyMnemonicsIn(items),
      options.autoSelection, options.preselectCondition != null ? options.preselectCondition :
                            action -> options.defaultIndex >= 0 &&
                                      options.defaultIndex < items.size() &&
                                      items.get(options.defaultIndex).getAction().equals(action));
    return new ActionPopupStep(items, title, contextSupplier, actionPlace, presentationFactory, stepOptions);
  }

  /** @deprecated Use {@link #createActionItems(ActionGroup, DataContext, String, PresentationFactory, boolean, boolean, boolean, boolean)} instead */
  @Deprecated(forRemoval = true)
  public static @NotNull List<PopupFactoryImpl.ActionItem> createActionItems(@NotNull ActionGroup actionGroup,
                                                                             @NotNull DataContext dataContext,
                                                                             boolean showNumbers,
                                                                             boolean useAlphaAsNumbers,
                                                                             boolean showDisabledActions,
                                                                             boolean honorActionMnemonics,
                                                                             @Nullable String actionPlace,
                                                                             @Nullable PresentationFactory presentationFactory) {
    return createActionItems(actionGroup, dataContext,
                             actionPlace == null ? ActionPlaces.POPUP : actionPlace,
                             presentationFactory == null ? new PresentationFactory() : presentationFactory,
                             ActionPopupOptions.create(
                               showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics,
                               -1, false, null));
  }

  public static @NotNull List<PopupFactoryImpl.ActionItem> createActionItems(@NotNull ActionGroup actionGroup,
                                                                             @NotNull DataContext dataContext,
                                                                             @NotNull String actionPlace,
                                                                             @NotNull PresentationFactory presentationFactory,
                                                                             @NotNull ActionPopupOptions options) {
    if (!isPopupOrMainMenuPlace(actionPlace)) {
      LOG.error("isPopupOrMainMenuPlace(" + actionPlace + ")==false. Use ActionPlaces.getPopupPlace.");
      actionPlace = getPopupOrMainMenuPlace(actionPlace);
    }
    ActionStepBuilder builder = new ActionStepBuilder(
      dataContext, options.showNumbers, options.useAlphaAsNumbers, options.showDisabledActions, options.honorActionMnemonics,
      actionPlace, presentationFactory);
    builder.buildGroup(actionGroup);
    return builder.getItems();
  }

  @Override
  public @NotNull List<PopupFactoryImpl.ActionItem> getValues() {
    return myItems;
  }

  public @NotNull List<PopupFactoryImpl.ActionItem> getInlineItems(@NotNull PopupFactoryImpl.ActionItem value) {
    return value.getInlineItems();
  }

  @Override
  public boolean isSelectable(final PopupFactoryImpl.ActionItem value) {
    return value.isEnabled();
  }

  @Override
  public int getMnemonicPos(final PopupFactoryImpl.ActionItem value) {
    String text = getTextFor(value);
    int i = text.indexOf(UIUtil.MNEMONIC);
    if (i < 0) i = text.indexOf('&');
    if (i < 0) i = text.indexOf('_');
    return i;
  }

  @Override
  public @Nullable String getMnemonicString(PopupFactoryImpl.ActionItem value) {
    if (value.digitMnemonicsEnabled()) {
      Character res = value.getMnemonicChar();
      return res != null ? res.toString() : null;
    }

    return MnemonicNavigationFilter.super.getMnemonicString(value);
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
  public @NotNull @NlsActions.ActionText String getTextFor(final PopupFactoryImpl.ActionItem value) {
    return value.getText();
  }

  @Override
  public @Nullable String getTooltipTextFor(PopupFactoryImpl.ActionItem value) {
    return value.getTooltip();
  }

  @Override
  public void setEmptyText(@NotNull StatusText emptyText) { }

  @Override
  public @Nullable String getSecondaryTextFor(PopupFactoryImpl.ActionItem item) {
    return item.getClientProperty(ActionUtil.SECONDARY_TEXT);
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
  public @Nullable String getTitle() {
    return myTitle;
  }

  @ApiStatus.Internal
  public @NotNull BiFunction<DataContext, AnAction, DataContext> getSubStepContextAdjuster() {
    return mySubStepContextAdjuster;
  }

  @ApiStatus.Internal
  public void setSubStepContextAdjuster(@NotNull BiFunction<DataContext, AnAction, DataContext>  subStepContextAdjuster) {
    mySubStepContextAdjuster = subStepContextAdjuster;
  }

  @Override
  public PopupStep<?> onChosen(PopupFactoryImpl.ActionItem actionChoice, boolean finalChoice) {
    return onChosen(actionChoice, finalChoice, null);
  }

  @Override
  public @Nullable PopupStep<?> onChosen(@NotNull PopupFactoryImpl.ActionItem item, boolean finalChoice, @Nullable InputEvent inputEvent) {
    if (!item.isEnabled()) return FINAL_CHOICE;
    AnAction action = item.getAction();
    if (action instanceof ActionGroup && (!finalChoice || !item.isPerformGroup())) {
      DataContext dataContext = mySubStepContextAdjuster.apply(myContext.get(), action);
      return getSubStep((ActionGroup)action, dataContext, myActionPlace, myPresentationFactory, myEnableMnemonics, true, myShowDisabledActions, null,
                        false, false, () -> dataContext, myPreselectActionCondition, -1);
    }
    else if (action instanceof ToggleAction && item.isKeepPopupOpen()) {
      performActionItem(item, inputEvent);
      return FINAL_CHOICE;
    }
    else {
      myFinalRunnable = () -> performActionItem(item, inputEvent);
      return FINAL_CHOICE;
    }
  }

  /** @noinspection SameParameterValue */
  protected @NotNull ListPopupStep<PopupFactoryImpl.ActionItem> getSubStep(
    @NotNull ActionGroup actionGroup, @NotNull DataContext dataContext,
    @NotNull String actionPlace, @NotNull PresentationFactory presentationFactory,
    boolean showNumbers, boolean useAlphaAsNumbers,
    boolean showDisabledActions, @PopupTitle @Nullable String title,
    boolean honorActionMnemonics, boolean autoSelectionEnabled,
    Supplier<? extends DataContext> contextSupplier,
    Condition<? super AnAction> preselectCondition, int defaultOptionIndex) {
    return createActionsStep(
      title, actionGroup, dataContext, actionPlace, presentationFactory, contextSupplier,
      ActionPopupOptions.forStepAndItems(showNumbers, useAlphaAsNumbers, showDisabledActions,
                                         honorActionMnemonics, autoSelectionEnabled, preselectCondition,
                                         defaultOptionIndex));
  }

  @Override
  public boolean isFinal(@NotNull PopupFactoryImpl.ActionItem item) {
    if (!item.isEnabled()) return true;
    return !(item.getAction() instanceof ActionGroup) || item.isPerformGroup();
  }

  public void performActionItem(@NotNull PopupFactoryImpl.ActionItem item, @Nullable InputEvent inputEvent) {
    DataContext dataContext = myContext.get();
    AnAction action = item.getAction();
    Presentation presentation = item.clonePresentation();
    AnActionEvent event = AnActionEvent.createFromInputEvent(inputEvent, myActionPlace, presentation, dataContext);
    event.setInjectedContext(action.isInInjectedContext());
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
  }

  public @NotNull AnActionEvent createAnActionEvent(@NotNull AnAction action, @Nullable InputEvent inputEvent) {
    Presentation presentation = myPresentationFactory.getPresentation(action);
    return AnActionEvent.createFromInputEvent(inputEvent, myActionPlace, presentation, myContext.get());
  }

  public void updateStepItems(@NotNull JComponent component) {
    DataContext dataContext = myContext.get();
    List<PopupFactoryImpl.ActionItem> values = getValues();
    Utils.updateComponentActions(
      component, ContainerUtil.map(values, PopupFactoryImpl.ActionItem::getAction),
      dataContext, myActionPlace, myPresentationFactory,
      () -> {
        for (PopupFactoryImpl.ActionItem actionItem : values) {
          Presentation presentation = myPresentationFactory.getPresentation(actionItem.getAction());
          actionItem.updateFromPresentation(presentation, myActionPlace);
          for (PopupFactoryImpl.ActionItem inlineActionItem : actionItem.getInlineItems()) {
            presentation = myPresentationFactory.getPresentation(inlineActionItem.getAction());
            inlineActionItem.updateFromPresentation(presentation, myActionPlace);
          }
        }
      }
    );
  }

  @Override
  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  @Override
  public boolean hasSubstep(final PopupFactoryImpl.ActionItem selectedValue) {
    return selectedValue != null && selectedValue.isEnabled() &&
           selectedValue.getAction() instanceof ActionGroup && !selectedValue.isSubstepSuppressed();
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

  @ApiStatus.Internal
  public void reorderItems(int from, int where, int preserveSeparatorAt) {
    if (myItems.get(from).isPrependWithSeparator() || myItems.get(where).isPrependWithSeparator()) {
      String fromText = myItems.get(from).getSeparatorText();
      String whereText = myItems.get(where).getSeparatorText();
      myItems.get(from).setSeparatorText(null);
      if (preserveSeparatorAt == from) {
        myItems.get(from + 1).setSeparatorText(fromText);
      }
      if (preserveSeparatorAt == where) {
        myItems.get(where).setSeparatorText(null);
        myItems.get(from).setSeparatorText(whereText);
      }
    }
    PopupFactoryImpl.ActionItem toMove = myItems.get(from);
    myItems.add(where, toMove);
    if (where < from) {
      from ++;
    }
    myItems.remove(from);
  }

  private static boolean isPopupOrMainMenuPlace(@NotNull String place) {
    return ActionPlaces.isPopupPlace(place) || ActionPlaces.MAIN_MENU.equals(place);
  }

  private static @NotNull String getPopupOrMainMenuPlace(@Nullable String place) {
    return place != null && isPopupOrMainMenuPlace(place) ? place : ActionPlaces.getPopupPlace(place);
  }
}
