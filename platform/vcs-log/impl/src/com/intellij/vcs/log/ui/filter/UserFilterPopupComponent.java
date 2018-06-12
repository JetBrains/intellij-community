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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUserFilterImpl;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Show a popup to select a user or enter the user name.
 */
class UserFilterPopupComponent extends MultipleValueFilterPopupComponent<VcsLogUserFilter> {
  @NotNull private final VcsLogData myLogData;
  @NotNull private final List<String> myAllUsers;

  UserFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                           @NotNull VcsLogData logData,
                           @NotNull FilterModel<VcsLogUserFilter> filterModel) {
    super("User", uiProperties, filterModel);
    myLogData = logData;
    myAllUsers = collectUsers(logData);
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogUserFilter filter) {
    return displayableText(myFilterModel.getFilterValues(filter));
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogUserFilter filter) {
    return tooltip(myFilterModel.getFilterValues(filter));
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(createAllAction());
    group.add(createSelectMultipleValuesAction());
    if (!myLogData.getCurrentUser().isEmpty()) {
      group.add(new PredefinedValueAction(VcsLogUserFilterImpl.ME));
    }
    group.addAll(createRecentItemsActionGroup());
    return group;
  }

  @NotNull
  protected ActionGroup createSpeedSearchActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new SpeedsearchPredefinedValueAction(VcsLogUserFilterImpl.ME));
    group.add(Separator.getInstance());
    for (String user : myAllUsers) {
      group.add(new SpeedsearchPredefinedValueAction(user));
    }
    return group;
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
    return myAllUsers;
  }

  @NotNull
  @Override
  protected ListPopup createPopupMenu() {
    ActionGroup actionGroup = createActionGroup();
    ActionGroup speedsearchGroup = createSpeedSearchActionGroup();
    return new UserLogSpeedSearchPopup(new DefaultActionGroup(actionGroup, speedsearchGroup),
                                       DataManager.getInstance().getDataContext(this));
  }

  @NotNull
  private static List<String> collectUsers(@NotNull VcsLogData logData) {
    List<String> users = ContainerUtil.map(logData.getAllUsers(), user -> {
      String shortPresentation = VcsUserUtil.getShortPresentation(user);
      Couple<String> firstAndLastName = VcsUserUtil.getFirstAndLastName(shortPresentation);
      if (firstAndLastName == null) return shortPresentation;
      return VcsUserUtil.capitalizeName(firstAndLastName.first) + " " + VcsUserUtil.capitalizeName(firstAndLastName.second);
    });
    TreeSet<String> sortedUniqueUsers = new TreeSet<>(users);
    return new ArrayList<>(sortedUniqueUsers);
  }

  private static class UserLogSpeedSearchPopup extends FlatSpeedSearchPopup {
    public UserLogSpeedSearchPopup(@NotNull DefaultActionGroup actionGroup, @NotNull DataContext dataContext) {
      super(null, actionGroup, dataContext, null, false);
      setMinimumSize(new JBDimension(200, 0));
    }

    @Override
    public boolean shouldBeShowing(@NotNull AnAction action) {
      if (!super.shouldBeShowing(action)) return false;
      if (getSpeedSearch().isHoldingFilter()) {
        if (action instanceof MultipleValueFilterPopupComponent.PredefinedValueAction) {
          return action instanceof SpeedsearchAction ||
                 ((MultipleValueFilterPopupComponent.PredefinedValueAction)action).myValues.size() > 1;
        }
        return true;
      }
      else {
        return !isSpeedsearchAction(action);
      }
    }
  }

  private class SpeedsearchPredefinedValueAction extends PredefinedValueAction implements FlatSpeedSearchPopup.SpeedsearchAction {
    public SpeedsearchPredefinedValueAction(String user) {super(user);}
  }
}