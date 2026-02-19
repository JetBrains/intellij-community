// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container for authentication data: login and password.
 * The password can be null intentionally: sometimes only login is known.
 *
 * @author Kirill Likhodedov
 */
public class AuthData {

  private final @NotNull String myLogin;
  private final @Nullable String myPassword;

  public AuthData(@NotNull String login, @Nullable String password) {
    myPassword = password;
    myLogin = login;
  }

  public @NotNull String getLogin() {
    return myLogin;
  }

  public @Nullable String getPassword() {
    return myPassword;
  }
}
