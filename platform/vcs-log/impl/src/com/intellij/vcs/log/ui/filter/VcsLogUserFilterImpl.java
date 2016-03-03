package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsLogUserFilterImpl implements VcsLogUserFilter {

  @NotNull public static final String ME = "me";

  @NotNull private final Collection<String> myUsers;
  @NotNull private final Map<VirtualFile, VcsUser> myData;
  @NotNull private final MultiMap<String, VcsUser> myAllUsersByNames = MultiMap.create();
  @NotNull private final MultiMap<String, VcsUser> myAllUsersByEmails = MultiMap.create();

  public VcsLogUserFilterImpl(@NotNull Collection<String> users,
                              @NotNull Map<VirtualFile, VcsUser> meData,
                              @NotNull Set<VcsUser> allUsers) {
    myUsers = users;
    myData = meData;

    for (VcsUser user : allUsers) {
      String name = user.getName();
      if (!name.isEmpty()) {
        myAllUsersByNames.putValue(name.toLowerCase(), user);
      }
      String email = user.getEmail();
      String emailNamePart = getEmailNamePart(email);
      if (emailNamePart != null) {
        myAllUsersByEmails.putValue(emailNamePart.toLowerCase(), user);
      }
    }
  }

  @Nullable
  private static String getEmailNamePart(@NotNull String email) {
    int at = email.indexOf('@');
    String emailNamePart = null;
    if (at > 0) {
      emailNamePart = email.substring(0, at);
    }
    return emailNamePart;
  }

  @NotNull
  @Override
  public Collection<String> getUserNames(@NotNull VirtualFile root) {
    Set<String> result = ContainerUtil.newHashSet();
    for (String user : myUsers) {
      Set<VcsUser> users = getUsers(root, user);
      if (!users.isEmpty()) {
        result.addAll(ContainerUtil.map(users, new Function<VcsUser, String>() {
          @Override
          public String fun(VcsUser user) {
            return userToString(user);
          }
        }));
      }
      else if (!user.equals(ME)) {
        result.add(user);
      }
    }
    return result;
  }

  @NotNull
  private Set<VcsUser> getUsers(@NotNull VirtualFile root, @NotNull String name) {
    Set<VcsUser> users = ContainerUtil.newHashSet();
    if (ME.equals(name)) {
      VcsUser vcsUser = myData.get(root);
      if (vcsUser != null) {
        users.addAll(getUsers(vcsUser.getName())); // do not just add vcsUser, also add synonyms
        String emailNamePart = getEmailNamePart(vcsUser.getEmail());
        if (emailNamePart != null) {
          users.addAll(getUsers(emailNamePart));
        }
      }
    }
    else {
      users.addAll(getUsers(name));
    }
    return users;
  }

  @NotNull
  public Collection<String> getUserNamesForPresentation() {
    return myUsers;
  }

  @Override
  public boolean matches(@NotNull final VcsCommitMetadata commit) {
    return ContainerUtil.exists(myUsers, new Condition<String>() {
      @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
      @Override
      public boolean value(String name) {
        Set<VcsUser> users = getUsers(commit.getRoot(), name);
        if (!users.isEmpty()) {
          return users.contains(commit.getAuthor());
        }
        else if (!name.equals(ME)) {
          String lowerUser = name.toLowerCase();
          return commit.getAuthor().getName().toLowerCase().equals(lowerUser) ||
                 commit.getAuthor().getEmail().toLowerCase().startsWith(lowerUser + "@");
        }
        return false;
      }
    });
  }

  private Set<VcsUser> getUsers(@NotNull String name) {
    Set<VcsUser> result = ContainerUtil.newHashSet();

    for (String variant : getSynonyms(name)) {
      result.addAll(myAllUsersByNames.get(variant.toLowerCase()));
      result.addAll(myAllUsersByEmails.get(variant.toLowerCase()));
    }

    return result;
  }

  @NotNull
  private static List<String> getSynonyms(@NotNull String name) {
    Pair<String, String> firstAndLastName = VcsUserImpl.getFirstAndLastName(name);
    if (firstAndLastName != null) {
      return Arrays.asList(firstAndLastName.first + " " + firstAndLastName.second,
                           firstAndLastName.first + "." + firstAndLastName.second,
                           firstAndLastName.first + firstAndLastName.second);
    }
    return Collections.singletonList(name);
  }

  @NotNull
  private static String userToString(@NotNull VcsUser user) {
    String name = user.getName();
    String email = user.getEmail();

    String result = name;
    if (!email.isEmpty()) {
      if (!name.isEmpty()) {
        result += " <";
      }
      result += email;

      if (!name.isEmpty()) {
        result += ">";
      }
    }

    return result;
  }
}