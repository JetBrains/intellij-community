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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Container for authentication data: host, login and password.
 *
 * @author Aleksey Pivovarov
 */
public class GithubAuthData {
  private final String myHost;
  private final String myLogin;
  private final String myPassword;

  public GithubAuthData(@NotNull String host, @NotNull String login, @NotNull String password) {
    myHost = host;
    myLogin = login;
    myPassword = password;
  }

  @NotNull
  public String getHost() {
    return StringUtil.notNullize(myHost);
  }

  @NotNull
  public String getLogin() {
    return StringUtil.notNullize(myLogin);
  }

  @NotNull
  public String getPassword() {
    return StringUtil.notNullize(myPassword);
  }

}
