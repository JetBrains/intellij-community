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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubUser {
  @NotNull private String myLogin;
  private long myId;

  @NotNull private String myUrl;
  @NotNull private String myHtmlUrl;

  @Nullable private String myGravatarId;

  @NotNull
  public static GithubUser create(@Nullable GithubUserRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.login == null) throw new JsonException("login is null");
      if (raw.id == null) throw new JsonException("id is null");
      if (raw.url == null) throw new JsonException("url is null");
      if (raw.htmlUrl == null) throw new JsonException("htmlUrl is null");

      return new GithubUser(raw.login, raw.id, raw.url, raw.htmlUrl, raw.gravatarId);
    }
    catch (JsonException e) {
      throw new JsonException("GithubUser parse error", e);
    }
  }

  protected GithubUser(@NotNull String login, long id, @NotNull String url, @NotNull String htmlUrl, @Nullable String gravatarId) {
    this.myLogin = login;
    this.myId = id;
    this.myUrl = url;
    this.myHtmlUrl = htmlUrl;
    this.myGravatarId = gravatarId;
  }

  protected GithubUser(@NotNull GithubUser user) {
    this.myLogin = user.myLogin;
    this.myId = user.myId;
    this.myUrl = user.myUrl;
    this.myHtmlUrl = user.myHtmlUrl;
  }

  @NotNull
  public String getLogin() {
    return myLogin;
  }

  public long getId() {
    return myId;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @Nullable
  public String getGravatarId() {
    return myGravatarId;
  }
}
