// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Note: users are considered equal if they have the same name and email. Emails are converted to a lower case in constructor.
 */
public final class VcsUserImpl implements VcsUser {
  private final @NotNull String myName;
  private final @NotNull String myEmail;

  public VcsUserImpl(@NotNull String name, @NotNull String email) {
    myName = name;
    myEmail = VcsUserUtil.emailToLowerCase(email);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getEmail() {
    return myEmail;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsUserImpl user = (VcsUserImpl)o;

    if (!myName.equals(user.myName)) return false;
    if (!myEmail.equals(user.myEmail)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myEmail);
  }

  @Override
  public String toString() {
    return VcsUserUtil.toExactString(this);
  }
}
