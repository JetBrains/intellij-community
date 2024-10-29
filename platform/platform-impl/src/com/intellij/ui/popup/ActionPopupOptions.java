// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionPopupOptions {
  private final boolean showNumbers;
  private final boolean useAlphaAsNumbers;
  private final boolean showDisabledActions;
  private final boolean honorActionMnemonics;
  private final int maxRowCount;
  private final boolean autoSelection;
  private final Condition<? super AnAction> preselectCondition;
  private final int defaultIndex;

  public boolean showNumbers() {
    return showNumbers;
  }

  public boolean useAlphaAsNumbers() {
    return useAlphaAsNumbers;
  }

  public boolean showDisabledActions() {
    return showDisabledActions;
  }

  public boolean honorActionMnemonics() {
    return honorActionMnemonics;
  }

  public int getMaxRowCount() {
    return maxRowCount;
  }

  public boolean autoSelectionEnabled() {
    return autoSelection;
  }

  public Condition<? super AnAction> getPreselectCondition() {
    return preselectCondition;
  }

  public int getDefaultIndex() {
    return defaultIndex;
  }

  public static @NotNull ActionPopupOptions empty() {
    return new ActionPopupOptions(false, false, false, false, -1, false, null, -1);
  }

  public static @NotNull ActionPopupOptions showDisabled() {
    return new ActionPopupOptions(false, false, true, false, -1, false, null, -1);
  }

  public static @NotNull ActionPopupOptions honorMnemonics() {
    return new ActionPopupOptions(false, false, false, true, -1, false, null, -1);
  }

  public static @NotNull ActionPopupOptions mnemonicsAndDisabled() {
    return new ActionPopupOptions(false, false, true, true, -1, false, null, -1);
  }

  public static @NotNull ActionPopupOptions forStep(boolean showDisabledActions,
                                                    boolean enableMnemonics,
                                                    boolean autoSelection,
                                                    @Nullable Condition<? super AnAction> preselectCondition) {
    return new ActionPopupOptions(
      false, false, showDisabledActions, enableMnemonics, -1,
      autoSelection, preselectCondition, -1);
  }

  public static @NotNull ActionPopupOptions forStepAndItems(boolean showNumbers,
                                                            boolean useAlphaAsNumbers,
                                                            boolean showDisabledActions,
                                                            boolean honorActionMnemonics,
                                                            boolean autoSelectionEnabled,
                                                            Condition<? super AnAction> preselectCondition,
                                                            int defaultOptionIndex) {
    return new ActionPopupOptions(
      showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, -1,
      autoSelectionEnabled, preselectCondition, defaultOptionIndex);
  }

  @ApiStatus.Internal
  public static @NotNull ActionPopupOptions forAid(@Nullable JBPopupFactory.ActionSelectionAid aid,
                                                   boolean showDisabledActions,
                                                   int maxRowCount,
                                                   @Nullable Condition<? super AnAction> preselectCondition) {
    return new ActionPopupOptions(
      aid == JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING || aid == JBPopupFactory.ActionSelectionAid.NUMBERING,
      aid == JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING,
      showDisabledActions,
      aid == JBPopupFactory.ActionSelectionAid.MNEMONICS,
      maxRowCount, false, preselectCondition, -1);
  }

  public static @NotNull ActionPopupOptions create(boolean showNumbers,
                                                   boolean useAlphaAsNumbers,
                                                   boolean showDisabledActions,
                                                   boolean honorActionMnemonics,
                                                   int maxRowCount,
                                                   boolean autoSelection,
                                                   Condition<? super AnAction> preselectCondition) {
    return new ActionPopupOptions(
      showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, maxRowCount,
      autoSelection, preselectCondition, -1);
  }

  private ActionPopupOptions(boolean showNumbers,
                             boolean useAlphaAsNumbers,
                             boolean showDisabledActions,
                             boolean honorActionMnemonics,
                             int maxRowCount,
                             boolean autoSelection,
                             @Nullable Condition<? super AnAction> preselectCondition,
                             int defaultIndex) {
    this.showNumbers = showNumbers;
    this.useAlphaAsNumbers = useAlphaAsNumbers;
    this.showDisabledActions = showDisabledActions;
    this.honorActionMnemonics = honorActionMnemonics;
    this.maxRowCount = maxRowCount;
    this.autoSelection = autoSelection;
    this.preselectCondition = preselectCondition;
    this.defaultIndex = defaultIndex;
  }
}
