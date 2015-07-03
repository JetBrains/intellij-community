package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class VcsLogUserFilterImpl implements VcsLogUserFilter {

  public @NotNull static final String ME = "me";

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