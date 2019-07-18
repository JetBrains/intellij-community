// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

//https://developer.github.com/v3/users/
//region GithubUser
/*
  "login": "octocat",
  "id": 1,
  "avatar_url": "https://github.com/images/error/octocat_happy.gif",
  "gravatar_id": "",
  "url": "https://api.github.com/users/octocat",
  "html_url": "https://github.com/octocat",
  "followers_url": "https://api.github.com/users/octocat/followers",
  "following_url": "https://api.github.com/users/octocat/following{/other_user}",
  "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
  "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
  "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
  "organizations_url": "https://api.github.com/users/octocat/orgs",
  "repos_url": "https://api.github.com/users/octocat/repos",
  "events_url": "https://api.github.com/users/octocat/events{/privacy}",
  "received_events_url": "https://api.github.com/users/octocat/received_events",
  "type": "User",
  "site_admin": false
*/
//endregion
@SuppressWarnings("UnusedDeclaration")
public class GithubUser {
  @NotNull public static final GithubUser UNKNOWN = createUnknownUser();

  private String login;
  private Long id;
  private String avatarUrl;
  private String gravatarId;

  private String url;
  private String htmlUrl;

  private String type;
  private Boolean siteAdmin;

  @NotNull
  public String getLogin() {
    return login;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  @Nullable
  public String getAvatarUrl() {
    return avatarUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubUser)) return false;
    GithubUser user = (GithubUser)o;
    return id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @NotNull
  private static GithubUser createUnknownUser() {
    GithubUser user = new GithubUser();
    user.id = -1L;
    user.login = "ghost";
    return user;
  }
}
