/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author max
 */
public interface UsageView extends Disposable {
  /**
   * Returns {@link com.intellij.usages.UsageTarget} to look usages for
   */
  DataKey<UsageTarget[]> USAGE_TARGETS_KEY = DataKey.create("usageTarget");
  @Deprecated String USAGE_TARGETS = USAGE_TARGETS_KEY.getName();

  /**
   * Returns {@link com.intellij.usages.Usage} which are selected in usage view
   */
  DataKey<Usage[]> USAGES_KEY = DataKey.create("usages");
  @Deprecated String USAGES = USAGES_KEY.getName();

  DataKey<UsageView> USAGE_VIEW_KEY = DataKey.create("UsageView.new");
  @Deprecated String USAGE_VIEW = USAGE_VIEW_KEY.getName();

  DataKey<UsageInfo> USAGE_INFO_KEY = DataKey.create("UsageInfo");
  DataKey<SearchScope> USAGE_SCOPE = DataKey.create("UsageScope");

  DataKey<List<UsageInfo>> USAGE_INFO_LIST_KEY = DataKey.create("UsageInfo.List");

  void appendUsage(@NotNull Usage usage);
  void removeUsage(@NotNull Usage usage);
  void includeUsages(@NotNull Usage[] usages);
  void excludeUsages(@NotNull Usage[] usages);
  void selectUsages(@NotNull Usage[] usages);

  void close();
  boolean isSearchInProgress();

  /**
   * @deprecated please specify mnemonic by prefixing the mnenonic character with an ampersand (&& for Mac-specific ampersands)
   */
  void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull String text, char mnemonic);
  void addButtonToLowerPane(@NotNull Runnable runnable, @NotNull String text);

  void setAdditionalComponent(@Nullable JComponent component);

  void addPerformOperationAction(@NotNull Runnable processRunnable, String commandName, String cannotMakeString, @NotNull String shortDescription);

  /**
   * @param checkReadOnlyStatus if false, check is performed inside processRunnable
   */
  void addPerformOperationAction(@NotNull Runnable processRunnable, String commandName, String cannotMakeString, @NotNull String shortDescription, boolean checkReadOnlyStatus);

  @NotNull
  UsageViewPresentation getPresentation();

  @NotNull
  Set<Usage> getExcludedUsages();

  @Nullable
  Set<Usage> getSelectedUsages();
  @NotNull Set<Usage> getUsages();
  @NotNull List<Usage> getSortedUsages();

  @NotNull JComponent getComponent();

  int getUsagesCount();

  /**
   * Removes all specified usages from the usage view in one heroic swoop.
   * Reloads the whole tree model once instead of firing individual remove event for each node.
   * Useful for processing huge number of usages faster, e.g. during "find in path/replace all".
   */
  void removeUsagesBulk(@NotNull Collection<Usage> usages);
}
