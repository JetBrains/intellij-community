// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.ui.FlatSpeedSearchPopup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Supplier;

/**
 * Show a popup to select a user or enter the user name.
 */
public class UserFilterPopupComponent
  extends MultipleValueFilterPopupComponent<VcsLogUserFilter, FilterModel.SingleFilterModel<VcsLogUserFilter>> {
  private final @NotNull VcsLogData myLogData;

  UserFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                           @NotNull VcsLogData logData,
                           @NotNull FilterModel.SingleFilterModel<VcsLogUserFilter> filterModel) {
    super("User", VcsLogBundle.messagePointer("vcs.log.user.filter.label"), uiProperties, filterModel);
    myLogData = logData;
  }

  @Override
  protected @NotNull ActionGroup createActionGroup() {
    List<AnAction> group = new ArrayList<>();
    group.add(createSelectMultipleValuesAction());
    if (!myLogData.getCurrentUser().isEmpty()) {
      group.add(new PredefinedValueAction(Collections.singletonList(VcsLogFilterObject.ME), () -> me(), true));
    }
    group.addAll(createRecentItemsActionGroup());
    return new DefaultActionGroup(group);
  }

  protected @NotNull ActionGroup createSpeedSearchActionGroup() {
    List<AnAction> group = new ArrayList<>();
    group.add(new SpeedsearchPredefinedValueAction(VcsLogFilterObject.ME, () -> me()));
    group.add(Separator.getInstance());
    for (String user : collectUsers(myLogData)) {
      group.add(new SpeedsearchPredefinedValueAction(user, () -> user));
    }
    return new DefaultActionGroup(group);
  }

  @Override
  protected @Unmodifiable @NotNull List<String> getAllValues() {
    return ContainerUtil.concat(Collections.singletonList(me()), collectUsers(myLogData));
  }

  @Override
  protected @NotNull ListPopup createPopupMenu() {
    ActionGroup actionGroup = createActionGroup();
    ActionGroup speedsearchGroup = createSpeedSearchActionGroup();
    return new UserLogSpeedSearchPopup(new DefaultActionGroup(actionGroup, speedsearchGroup),
                                       DataManager.getInstance().getDataContext(this));
  }

  @Override
  protected @Nullable VcsLogUserFilter createFilter(@NotNull List<String> values) {
    return myFilterModel.createFilter(values);
  }

  @Override
  protected @NotNull List<String> getFilterValues(@NotNull VcsLogUserFilter filter) {
    return myFilterModel.getFilterValues(filter);
  }

  @Override
  protected @NotNull List<String> parseLocalizedValues(@NotNull Collection<String> values) {
    return ContainerUtil.map(values, user -> user.equals(me()) ? VcsLogFilterObject.ME : user);
  }

  @Override
  protected @NotNull List<String> getLocalizedValues(@NotNull Collection<String> values) {
    return ContainerUtil.map(values, user -> user.equals(VcsLogFilterObject.ME) ? me() : user);
  }

  private static @NotNull String me() {
    return VcsLogBundle.message("vcs.log.user.filter.me");
  }

  private static @NotNull List<String> collectUsers(@NotNull VcsLogData logData) {
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
    UserLogSpeedSearchPopup(@NotNull DefaultActionGroup actionGroup, @NotNull DataContext dataContext) {
      super(null, actionGroup, dataContext, ActionPlaces.getPopupPlace("VCS.Log.UserWidget"), null, false);
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
    SpeedsearchPredefinedValueAction(@NotNull String user, @NotNull Supplier<String> supplier) {
      super(Collections.singletonList(user), supplier, true);
    }
  }
}