// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private String login;
  private Long id;
  private String nodeId;
  private String avatarUrl;
  private String gravatarId;

  private String url;
  private String htmlUrl;

  private String type;
  private Boolean siteAdmin;

  public @NotNull String getNodeId() {
    return nodeId;
  }

  public @NotNull String getLogin() {
    return login;
  }

  public @NotNull String getHtmlUrl() {
    return htmlUrl;
  }

  public @Nullable String getAvatarUrl() {
    return avatarUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubUser user)) return false;
    return id.equals(user.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
