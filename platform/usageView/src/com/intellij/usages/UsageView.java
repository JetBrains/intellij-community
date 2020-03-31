// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UsageView extends Disposable {
  /**
   * Returns {@link UsageTarget} to look usages for
   */
  DataKey<UsageTarget[]> USAGE_TARGETS_KEY = DataKey.create("usageTarget");

  /**
   * Returns {@link Usage} which are selected in usage view
   */
  DataKey<Usage[]> USAGES_KEY = DataKey.create("usages");

  DataKey<UsageView> USAGE_VIEW_KEY = DataKey.create("UsageView.new");

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

  /**
   * @deprecated please specify mnemonic by prefixing the mnemonic character with an ampersand (&& for Mac-specific ampersands)
   */
  @Deprecated
  void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull String text, char mnemonic);
  void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull String text);
  void addButtonToLowerPane(@NotNull Action action);

  /**
   * @deprecated see {@link UsageView#setRerunAction(Action)}
   */
  @Deprecated
  default void setReRunActivity(@NotNull Runnable runnable) {}

  /**
   * @param rerunAction this action is used to provide non-standard search restart. Disabled action makes toolbar button disabled too.
   */
  default void setRerunAction(@NotNull Action rerunAction) {}

  void setAdditionalComponent(@Nullable JComponent component);

  void addPerformOperationAction(@NotNull Runnable processRunnable, @NotNull String commandName, String cannotMakeString, @NotNull String shortDescription);

  /**
   * @param checkReadOnlyStatus if false, check is performed inside processRunnable
   */
  void addPerformOperationAction(@NotNull Runnable processRunnable, @NotNull String commandName, String cannotMakeString, @NotNull String shortDescription, boolean checkReadOnlyStatus);

  @NotNull
  UsageViewPresentation getPresentation();

  @NotNull
  Set<Usage> getExcludedUsages();

  @NotNull
  Set<Usage> getSelectedUsages();

  @NotNull
  Set<Usage> getUsages();

  @NotNull
  List<Usage> getSortedUsages();

  @NotNull
  JComponent getComponent();

  @NotNull
  default JComponent getPreferredFocusableComponent() {
    return getComponent();
  }

  int getUsagesCount();

  /**
   * Removes all specified usages from the usage view in one heroic swoop.
   * Reloads the whole tree model once instead of firing individual remove event for each node.
   * Useful for processing huge number of usages faster, e.g. during "find in path/replace all".
   */
  void removeUsagesBulk(@NotNull Collection<? extends Usage> usages);

  default void addExcludeListener(@NotNull Disposable disposable, @NotNull ExcludeListener listener){}

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
