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
package org.jetbrains.plugins.github;

import org.jetbrains.annotations.NotNull;

/**
 * Container for authentication data: host, login and password.
 *
 * @author Aleksey Pivovarov
 */
public class GithubAuthData {
  private String myHost;
  private String myLogin;
  private String myPassword;

  public GithubAuthData(String host, String login, String password) {
    myHost = host;
    myLogin = login;
    myPassword = password;
  }

  @NotNull
  public String getHost() {
    return myHost != null ? myHost : "";
  }

  @NotNull
  public String getLogin() {
    return myLogin != null ? myLogin : "";
  }

  @NotNull
  public String getPassword() {
    return myPassword != null ? myPassword : "";
  }

  public void set(@NotNull GithubAuthData auth) {
    myHost = auth.getHost();
    myLogin = auth.getLogin();
    myPassword = auth.getPassword();
  }
}
