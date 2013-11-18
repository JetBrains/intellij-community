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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.data.VcsLogUserFilter;
import com.intellij.vcs.log.ui.PopupWithTextFieldWithAutoCompletion;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Show a popup to select a user or enter the user name.
 */
class UserFilterPopupComponent extends FilterPopupComponent {

  private static final String ME = "me";
  private final VcsLogDataHolder myDataHolder;
  private final VcsLogUiProperties myUiProperties;

  UserFilterPopupComponent(VcsLogClassicFilterUi filterUi, VcsLogDataHolder dataHolder, VcsLogUiProperties uiProperties) {
    super(filterUi, "User");
    myDataHolder = dataHolder;
    myUiProperties = uiProperties;
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(createAllAction());
    group.add(new SetValueAction(ME, this));

    List<String> recentlyFilteredUsers = myUiProperties.getRecentlyFilteredUsers();
    if (!recentlyFilteredUsers.isEmpty()) {
      group.addSeparator("Recently searched");
      for (String recentUser : recentlyFilteredUsers) {
        group.add(new SetValueAction(recentUser, this));
      }
    }
    group.addSeparator();
    group.add(new SelectUserAction());
    return group;
  }

  @Nullable
  @Override
  protected VcsLogFilter getFilter() {
    String value = getValue();
    if (value == ALL) {
      return null;
    }
    if (value == ME) {
      return new VcsLogUserFilter.Me(myDataHolder.getCurrentUser());
    }
    myUiProperties.addRecentlyFilteredUser(value);
    return new VcsLogUserFilter.ByName(value);
  }

  private class SelectUserAction extends DumbAwareAction {

    SelectUserAction() {
      super("Select...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Collection<String> users = ContainerUtil.map(myDataHolder.getAllUsers(), new Function<VcsUser, String>() {
        @Override
        public String fun(VcsUser user) {
          return user.getName();
        }
      });

      final PopupWithTextFieldWithAutoCompletion textField = new PopupWithTextFieldWithAutoCompletion(e.getProject(), users);
      JBPopup popup = textField.createPopup();

      popup.addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          if (event.isOk()) {
            String user = textField.getText();
            setValue(user);
            applyFilters();
          }
        }
      });
      popup.showUnderneathOf(UserFilterPopupComponent.this);
    }

  }
}
