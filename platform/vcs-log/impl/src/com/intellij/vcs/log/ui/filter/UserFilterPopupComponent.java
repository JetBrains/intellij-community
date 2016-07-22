/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Show a popup to select a user or enter the user name.
 */
class UserFilterPopupComponent extends MultipleValueFilterPopupComponent<VcsLogUserFilter> {
  @NotNull private final VcsLogData myLogData;

  UserFilterPopupComponent(@NotNull VcsLogUiProperties uiProperties,
                           @NotNull VcsLogData logData,
                           @NotNull FilterModel<VcsLogUserFilter> filterModel) {
    super("User", uiProperties, filterModel);
    myLogData = logData;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogUserFilter filter) {
    return displayableText(getTextValues(filter));
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogUserFilter filter) {
    return tooltip(getTextValues(filter));
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(createAllAction());
    group.add(createSelectMultipleValuesAction());
    if (!myLogData.getCurrentUser().isEmpty()) {
      group.add(createPredefinedValueAction(Collections.singleton(VcsLogUserFilterImpl.ME)));
    }
    group.addAll(createRecentItemsActionGroup());
    return group;
  }

  @NotNull
  @Override
  protected Collection<String> getTextValues(@Nullable VcsLogUserFilter filter) {
    if (filter == null) {
      return Collections.emptySet();
    }
    return ContainerUtil.newHashSet(((VcsLogUserFilterImpl)filter).getUserNamesForPresentation());
  }

  @NotNull
  @Override
  protected List<List<String>> getRecentValuesFromSettings() {
    return myUiProperties.getRecentlyFilteredUserGroups();
  }

  @Override
  protected void rememberValuesInSettings(@NotNull Collection<String> values) {
    myUiProperties.addRecentlyFilteredUserGroup(new ArrayList<>(values));
  }

  @NotNull
  @Override
  protected List<String> getAllValues() {
    return ContainerUtil.map(myLogData.getAllUsers(), user -> {
      String shortPresentation = VcsUserUtil.getShortPresentation(user);
      Couple<String> firstAndLastName = VcsUserUtil.getFirstAndLastName(shortPresentation);
      if (firstAndLastName == null) return shortPresentation;
      return VcsUserUtil.capitalizeName(firstAndLastName.first) + " " + VcsUserUtil.capitalizeName(firstAndLastName.second);
    });
  }

  @NotNull
  @Override
  protected VcsLogUserFilter createFilter(@NotNull Collection<String> values) {
    return new VcsLogUserFilterImpl(values, myLogData.getCurrentUser(), myLogData.getAllUsers());
  }
}