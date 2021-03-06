// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.cloud;

import org.jetbrains.annotations.NotNull;

public final class AppEngineAuthData {
  private final boolean myOAuth2;
  private final String myEmail;
  private final String myPassword;

  @NotNull
  public static AppEngineAuthData oauth2() {
    return new AppEngineAuthData(true, null, null);
  }

  @NotNull
  public static AppEngineAuthData login(@NotNull String email, @NotNull String password) {
    return new AppEngineAuthData(false, email, password);
  }

  private AppEngineAuthData(boolean OAuth2, String email, String password) {
    myOAuth2 = OAuth2;
    myEmail = email;
    myPassword = password;
  }

  public boolean isOAuth2() {
    return myOAuth2;
  }

  public String getEmail() {
    return myEmail;
  }

  public String getPassword() {
    return myPassword;
  }
}
