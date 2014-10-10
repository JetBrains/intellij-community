/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.appengine.cloud;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AppEngineAuthData {
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
