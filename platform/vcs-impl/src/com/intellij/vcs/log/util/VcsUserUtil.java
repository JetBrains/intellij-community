// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsUser;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VcsUserUtil {
  @NotNull private static final Pattern NAME_PATTERN = Pattern.compile("(\\w+)[\\W_](\\w+)");
  @NotNull private static final Pattern PRINTABLE_ASCII_PATTERN = Pattern.compile("[ -~]*");

  @NotNull
  public static String toExactString(@NotNull VcsUser user) {
    return getString(user.getName(), user.getEmail());
  }

  @NotNull
  private static String getString(@NotNull String name, @NotNull String email) {
    if (name.isEmpty()) return email;
    if (email.isEmpty()) return name;
    return name + " <" + email + ">";
  }

  public static boolean isSamePerson(@NotNull VcsUser user1, @NotNull VcsUser user2) {
    return getNameInStandardForm(getName(user1)).equals(getNameInStandardForm(getName(user2)));
  }

  @NotNull
  public static String getShortPresentation(@NotNull VcsUser user) {
    return getName(user);
  }

  @NotNull
  private static String getName(@NotNull VcsUser user) {
    return getUserName(user.getName(), user.getEmail());
  }

  @NotNull
  public static String getUserName(@NotNull String name, @NotNull String email) {
    if (!name.isEmpty()) return name;
    String emailNamePart = getNameFromEmail(email);
    if (emailNamePart != null) return emailNamePart;
    return email;
  }

  @Nullable
  public static String getNameFromEmail(@NotNull String email) {
    int at = email.indexOf('@');
    String emailNamePart = null;
    if (at > 0) {
      emailNamePart = email.substring(0, at);
    }
    return emailNamePart;
  }

  @NotNull
  public static String getNameInStandardForm(@NotNull String name) {
    Couple<String> firstAndLastName = getFirstAndLastName(name);
    if (firstAndLastName != null) {
      return StringUtil.toLowerCase(firstAndLastName.first) + " " + StringUtil.toLowerCase(firstAndLastName.second); // synonyms detection is currently english-only
    }
    return nameToLowerCase(name);
  }

  @Nullable
  public static Couple<String> getFirstAndLastName(@NotNull String name) {
    Matcher matcher = NAME_PATTERN.matcher(name);
    if (matcher.matches()) {
      return Couple.of(matcher.group(1), matcher.group(2));
    }
    return null;
  }

  @NotNull
  public static String nameToLowerCase(@NotNull String name) {
    if (!PRINTABLE_ASCII_PATTERN.matcher(name).matches()) return name;
    return StringUtil.toLowerCase(name);
  }

  @NotNull
  public static String capitalizeName(@NotNull String name) {
    if (name.isEmpty()) return name;
    if (!PRINTABLE_ASCII_PATTERN.matcher(name).matches()) return name;
    return StringUtil.toUpperCase(name.substring(0, 1)) + name.substring(1);
  }

  @NotNull
  public static String emailToLowerCase(@NotNull String email) {
    return StringUtil.toLowerCase(email);
  }

  public static class VcsUserHashingStrategy implements TObjectHashingStrategy<VcsUser> {
    @Override
    public int computeHashCode(VcsUser user) {
      return getNameInStandardForm(getName(user)).hashCode();
    }

    @Override
    public boolean equals(VcsUser user1, VcsUser user2) {
      return isSamePerson(user1, user2);
    }
  }
}
