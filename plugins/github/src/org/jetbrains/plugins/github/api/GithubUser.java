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

import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubUtil;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubUser {
  @NotNull private String login;
  private long id;

  @NotNull private String url;
  @NotNull private String htmlUrl;

  @Nullable
  public static GithubUser create(@Nullable GithubUserRaw raw) {
    try {
      if (raw == null) throw new JsonParseException("raw is null");
      if (raw.login == null) throw new JsonParseException("login is null");
      if (raw.id == null) throw new JsonParseException("id is null");
      if (raw.url == null) throw new JsonParseException("url is null");
      if (raw.htmlUrl == null) throw new JsonParseException("htmlUrl is null");

      return new GithubUser(raw.login, raw.id, raw.url, raw.htmlUrl);
    }
    catch (JsonParseException e) {
      GithubUtil.LOG.info("GithubUser parse error: " + e.getMessage());
      return null;
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
