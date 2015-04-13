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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Show a popup to select a user or enter the user name.
 */
class UserFilterPopupComponent extends MultipleValueFilterPopupComponent<VcsLogUserFilter> {

  private static final String ME = "me";

  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogUiProperties myUiProperties;

  UserFilterPopupComponent(@NotNull VcsLogUiProperties uiProperties,
                           @NotNull VcsLogDataHolder dataHolder,
                           @NotNull FilterModel<VcsLogUserFilter> filterModel) {
    super("User", uiProperties, filterModel);
    myDataHolder = dataHolder;
    myUiProperties = uiProperties;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogUserFilter filter) {
    return displayableText(getValues(filter));
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogUserFilter filter) {
    return tooltip(getValues(filter));
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(createAllAction());
    group.add(createSelectMultipleValuesAction());
    if (!myDataHolder.getCurrentUser().isEmpty()) {
      group.add(createPredefinedValueAction(Collections.singleton(ME)));
    }
    group.addAll(createRecentItemsActionGroup());
    return group;
  }

  @NotNull
  @Override
  protected Collection<String> getValues(@Nullable VcsLogUserFilter filter) {
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
    myUiProperties.addRecentlyFilteredUserGroup(new ArrayList<String>(values));
  }

  @NotNull
  @Override
  protected List<String> getAllValues() {
    return ContainerUtil.map(myDataHolder.getAllUsers(), new Function<VcsUser, String>() {
      @Override
      public String fun(VcsUser user) {
        return user.getName();
      }
    });
  }

  @NotNull
  @Override
  protected VcsLogUserFilter createFilter(@NotNull Collection<String> values) {
    return new VcsLogUserFilterImpl(values, myDataHolder.getCurrentUser(), myDataHolder.getAllUsers());
  }

  private static class VcsLogUserFilterImpl implements VcsLogUserFilter {

    @NotNull private final Collection<String> myUsers;
    @NotNull private final Map<VirtualFile, VcsUser> myData;
    @NotNull private final Collection<String> myAllUserNames;

    public VcsLogUserFilterImpl(@NotNull Collection<String> users,
                                @NotNull Map<VirtualFile, VcsUser> meData,
                                @NotNull Set<VcsUser> allUsers) {
      myUsers = users;
      myData = meData;
      myAllUserNames = ContainerUtil.mapNotNull(allUsers, new Function<VcsUser, String>() {
        @Override
        public String fun(VcsUser vcsUser) {
          String name = vcsUser.getName();
          if (!name.isEmpty()) {
            return name.toLowerCase();
          }
          String email = vcsUser.getEmail();
          int at = email.indexOf('@');
          if (at > 0) {
            return email.substring(0, at).toLowerCase();
          }

          return null;
        }
      });
    }

    @NotNull
    @Override
    public Collection<String> getUserNames(@NotNull final VirtualFile root) {
      Set<String> result = ContainerUtil.newHashSet();
      for (String user : myUsers) {
        if (ME.equals(user)) {
          VcsUser vcsUser = myData.get(root);
          if (vcsUser != null) {
            result.addAll(getVariants(vcsUser.getName()));
          }
        }
        else {
          result.addAll(getVariants(user));
        }
      }
      return result;
    }

    @NotNull
    public Collection<String> getUserNamesForPresentation() {
      return myUsers;
    }

    @Override
    public boolean matches(@NotNull final VcsCommitMetadata commit) {
      return ContainerUtil.exists(getUserNames(commit.getRoot()), new Condition<String>() {
        @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
        @Override
        public boolean value(String user) {
          String lowerUser = user.toLowerCase();
          return commit.getAuthor().getName().toLowerCase().equals(lowerUser) ||
                 commit.getAuthor().getEmail().toLowerCase().startsWith(lowerUser + "@");
        }
      });
    }

    @NotNull
    public Set<String> getVariants(@NotNull String name) {
      Set<String> result = ContainerUtil.newHashSet(name);

      Pair<String, String> firstAndLastName = VcsUserImpl.getFirstAndLastName(name);
      if (firstAndLastName != null) {
        result.addAll(ContainerUtil.filter(Arrays.asList(firstAndLastName.first + " " + firstAndLastName.second,
                                                         firstAndLastName.first + "." + firstAndLastName.second,
                                                         firstAndLastName.first + firstAndLastName.second), new Condition<String>() {
          @Override
          public boolean value(String s) {
            return myAllUserNames.contains(s.toLowerCase());
          }
        }));
      }

      return result;
    }
  }
}
