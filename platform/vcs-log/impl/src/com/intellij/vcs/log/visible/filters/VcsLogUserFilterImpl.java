// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
class VcsLogUserFilterImpl implements VcsLogUserFilter {
  private static final Logger LOG = Logger.getInstance(VcsLogUserFilterImpl.class);

  @NotNull private final Collection<String> myUsers;
  @NotNull private final Map<VirtualFile, VcsUser> myData;
  @NotNull private final MultiMap<String, VcsUser> myAllUsersByNames = MultiMap.create();
  @NotNull private final MultiMap<String, VcsUser> myAllUsersByEmails = MultiMap.create();

  VcsLogUserFilterImpl(@NotNull Collection<String> users,
                       @NotNull Map<VirtualFile, VcsUser> meData,
                       @NotNull Set<? extends VcsUser> allUsers) {
    myUsers = users;
    myData = meData;

    for (VcsUser user : allUsers) {
      String name = user.getName();
      if (!name.isEmpty()) {
        myAllUsersByNames.putValue(VcsUserUtil.getNameInStandardForm(name), user);
      }
      String email = user.getEmail();
      String nameFromEmail = VcsUserUtil.getNameFromEmail(email);
      if (nameFromEmail != null) {
        myAllUsersByEmails.putValue(VcsUserUtil.getNameInStandardForm(nameFromEmail), user);
      }
    }
  }

  @Override
  @NotNull
  public Collection<VcsUser> getUsers(@NotNull VirtualFile root) {
    Set<VcsUser> result = new HashSet<>();
    for (String user : myUsers) {
      result.addAll(getUsers(root, user));
    }
    return result;
  }

  @NotNull
  private Set<VcsUser> getUsers(@NotNull VirtualFile root, @NotNull String name) {
    Set<VcsUser> users = new HashSet<>();
    if (VcsLogFilterObject.ME.equals(name)) {
      VcsUser vcsUser = myData.get(root);
      if (vcsUser != null) {
        users.addAll(getUsers(vcsUser.getName())); // do not just add vcsUser, also add synonyms
        String emailNamePart = VcsUserUtil.getNameFromEmail(vcsUser.getEmail());
        if (emailNamePart != null) {
          Set<String> emails = ContainerUtil.map2Set(users, user -> VcsUserUtil.emailToLowerCase(user.getEmail()));
          for (VcsUser candidateUser : getUsers(emailNamePart)) {
            if (emails.contains(VcsUserUtil.emailToLowerCase(candidateUser.getEmail()))) {
              users.add(candidateUser);
            }
          }
        }
      }
      else {
        LOG.warn("Can not resolve user name for root " + root);
      }
    }
    else {
      users.addAll(getUsers(name));
    }
    return users;
  }

  @NotNull
  @Override
  public Collection<String> getValuesAsText() {
    return myUsers;
  }

  @Override
  public boolean matches(@NotNull final VcsCommitMetadata commit) {
    return ContainerUtil.exists(myUsers, name -> {
      Set<VcsUser> users = getUsers(commit.getRoot(), name);
      if (!users.isEmpty()) {
        return users.contains(commit.getAuthor());
      }
      else if (!name.equals(VcsLogFilterObject.ME)) {
        String lowerUser = VcsUserUtil.nameToLowerCase(name);
        boolean result = VcsUserUtil.nameToLowerCase(commit.getAuthor().getName()).equals(lowerUser) ||
                         VcsUserUtil.emailToLowerCase(commit.getAuthor().getEmail()).startsWith(lowerUser + "@");
        if (result) {
          LOG.warn("Unregistered author " + commit.getAuthor() + " for commit " + commit.getId().asString() + "; search pattern " + name);
        }
        return result;
      }
      return false;
    });
  }

  @NotNull
  private Set<VcsUser> getUsers(@NotNull String name) {
    String standardName = VcsUserUtil.getNameInStandardForm(name);

    Set<VcsUser> result = new HashSet<>();
    result.addAll(myAllUsersByNames.get(standardName));
    result.addAll(myAllUsersByEmails.get(standardName));
    return result;
  }

  @Override
  public String toString() {
    return "author: " + StringUtil.join(myUsers, ", ");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogUserFilterImpl filter = (VcsLogUserFilterImpl)o;
    return Comparing.haveEqualElements(myUsers, filter.myUsers);
  }

  @Override
  public int hashCode() {
    return Comparing.unorderedHashcode(myUsers);
  }
}