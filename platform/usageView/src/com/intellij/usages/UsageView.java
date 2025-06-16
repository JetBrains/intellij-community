// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public interface UsageView extends Disposable {
  /**
   * Returns {@link UsageTarget} to look usages for
   * @see com.intellij.ide.impl.dataRules.UsageTargetsRule
   */
  DataKey<UsageTarget[]> USAGE_TARGETS_KEY = DataKey.create("usageTarget");

  AtomicInteger COUNTER = new AtomicInteger();
  /**
   * Returns {@link Usage} which are selected in usage view
   */
  DataKey<Usage[]> USAGES_KEY = DataKey.create("usages");

  DataKey<UsageView> USAGE_VIEW_KEY = DataKey.create("UsageView.new");
  DataKey<UsageViewSettings> USAGE_VIEW_SETTINGS_KEY = DataKey.create("UsageViewSettings");

  DataKey<UsageInfo> USAGE_INFO_KEY = DataKey.create("UsageInfo");
  DataKey<SearchScope> USAGE_SCOPE = DataKey.create("UsageScope");

  DataKey<List<UsageInfo>> USAGE_INFO_LIST_KEY = DataKey.create("UsageInfo.List");

  void appendUsage(@NotNull Usage usage);
  void removeUsage(@NotNull Usage usage);
  void includeUsages(Usage @NotNull [] usages);
  void excludeUsages(Usage @NotNull [] usages);
  void selectUsages(Usage @NotNull [] usages);

  void close();
  boolean isSearchInProgress();

  void addButtonToLowerPane(@NotNull Runnable runnable, @NlsContexts.Button @NotNull String text);
  void addButtonToLowerPane(@NotNull Action action);

  /**
   * @param rerunAction this action is used to provide non-standard search restart. Disabled action makes toolbar button disabled too.
   */
  default void setRerunAction(@NotNull Action rerunAction) {}

  void setAdditionalComponent(@Nullable JComponent component);

  /**
   * @param cannotMakeString pass empty string to avoid "cannot perform" checks e.g., for explicit reruns
   */
  void addPerformOperationAction(@NotNull Runnable processRunnable,
                                 @Nullable @NlsContexts.Command String commandName,
                                 @NotNull @NlsContexts.DialogMessage String cannotMakeString,
                                 @NotNull @NlsContexts.Button String shortDescription);

  /**
   * @param cannotMakeString pass empty string to avoid "cannot perform" checks e.g., for explicit reruns
   * @param checkReadOnlyStatus if false, check is performed inside processRunnable
   */
  void addPerformOperationAction(@NotNull Runnable processRunnable, @Nullable String commandName, @NotNull String cannotMakeString, @NotNull String shortDescription, boolean checkReadOnlyStatus);

  @NotNull
  UsageViewPresentation getPresentation();

  @NotNull
  @Unmodifiable
  Set<Usage> getExcludedUsages();

  @NotNull
  @Unmodifiable
  Set<Usage> getSelectedUsages();

  @NotNull
  @Unmodifiable Set<Usage> getUsages();

  @NotNull
  @Unmodifiable List<Usage> getSortedUsages();

  @NotNull
  JComponent getComponent();

  default @NotNull JComponent getPreferredFocusableComponent() {
    return getComponent();
  }

  int getUsagesCount();

  /**
   * Removes all specified usages from the usage view in one heroic swoop.
   * Reloads the whole tree model once instead of firing individual remove event for each node.
   * Useful for processing huge number of usages faster, e.g. during "find in path/replace all".
   */
  void removeUsagesBulk(@NotNull Collection<? extends Usage> usages);

  default void addExcludeListener(@NotNull Disposable disposable, @NotNull ExcludeListener listener) {}

  @ApiStatus.Internal
  @IntellijInternalApi
  default int getId() {
    return -1;
  }

  @FunctionalInterface
  interface ExcludeListener {
    /**
     *
     * @param usages unmodifiable set or nodes that were excluded or included
     * @param excluded if {@code true} usages were excluded otherwise they were included
     */
    void fireExcluded(@NotNull Set<? extends Usage> usages, boolean excluded);
  }
}
