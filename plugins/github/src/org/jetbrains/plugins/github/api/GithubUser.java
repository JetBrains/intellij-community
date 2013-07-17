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
  @NotNull private String login;
  private long id;

  @NotNull private String url;
  @NotNull private String htmlUrl;

  @NotNull
  public static GithubUser create(@Nullable GithubUserRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.login == null) throw new JsonException("login is null");
      if (raw.id == null) throw new JsonException("id is null");
      if (raw.url == null) throw new JsonException("url is null");
      if (raw.htmlUrl == null) throw new JsonException("htmlUrl is null");

      return new GithubUser(raw.login, raw.id, raw.url, raw.htmlUrl);
    }
    catch (JsonException e) {
      throw new JsonException("GithubUser parse error", e);
    }
  }

  private GithubUser(@NotNull String login, long id, @NotNull String url, @NotNull String htmlUrl) {
    this.login = login;
    this.id = id;
    this.url = url;
    this.htmlUrl = htmlUrl;
  }

  protected GithubUser(@NotNull GithubUser user) {
    this.login = user.login;
    this.id = user.id;
    this.url = user.url;
    this.htmlUrl = user.htmlUrl;
  }

  @NotNull
  public String getLogin() {
    return login;
  }

  public long getId() {
    return id;
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }
}
