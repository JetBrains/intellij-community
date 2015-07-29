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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Note: users are considered equal if they have the same name, even if the e-mail is different.
 */
public class VcsUserImpl implements VcsUser {
  @NotNull private static final Pattern NAME_WITH_DOT = Pattern.compile("(\\w*)\\.(\\w*)");
  @NotNull private static final Pattern NAME_WITH_SPACE = Pattern.compile("(\\w*) (\\w*)");

  @NotNull private final String myName;
  @NotNull private final String myEmail;

  public VcsUserImpl(@NotNull String name, @NotNull String email) {
    myName = name;
    myEmail = email;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getEmail() {
    return myEmail;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsUserImpl user = (VcsUserImpl)o;

    if (!myName.equals(user.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    return myName + "<" + myEmail + ">";
  }

  public static boolean isSamePerson(@NotNull VcsUser user1, @NotNull VcsUser user2) {
    return getNameInStandardForm(user1.getName()).equals(getNameInStandardForm(user2.getName()));
  }

  @NotNull
  public static String getNameInStandardForm(@NotNull String name) {
    Pair<String, String> firstAndLastName = getFirstAndLastName(name);
    if (firstAndLastName != null) {
      return firstAndLastName.first.toLowerCase() + " " + firstAndLastName.second.toLowerCase();
    }
    return name.toLowerCase();
  }

  @Nullable
  public static Pair<String, String> getFirstAndLastName(@NotNull String name) {
    Matcher nameWithDotMatcher = NAME_WITH_DOT.matcher(name);
    if (nameWithDotMatcher.matches()) {
      return Pair.create(nameWithDotMatcher.group(1), nameWithDotMatcher.group(2));
    }
    Matcher nameWithSpaceMatcher = NAME_WITH_SPACE.matcher(name);
    if (nameWithSpaceMatcher.matches()) {
      return Pair.create(nameWithSpaceMatcher.group(1), nameWithSpaceMatcher.group(2));
    }
    return null;
  }
}
