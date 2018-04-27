// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.RestModel;

import java.util.Date;

//https://developer.github.com/v3/users/
//region GithubUserDetailed
//region GithubUser
/*
  "login":"octocat",
  "id":1,
  "avatar_url":"https://github.com/images/error/octocat_happy.gif",
  "gravatar_id":"",
  "url":"https://api.github.com/users/octocat",
  "html_url":"https://github.com/octocat",
  "followers_url":"https://api.github.com/users/octocat/followers",
  "following_url":"https://api.github.com/users/octocat/following{/other_user}",
  "gists_url":"https://api.github.com/users/octocat/gists{/gist_id}",
  "starred_url":"https://api.github.com/users/octocat/starred{/owner}{/repo}",
  "subscriptions_url":"https://api.github.com/users/octocat/subscriptions",
  "organizations_url":"https://api.github.com/users/octocat/orgs",
  "repos_url":"https://api.github.com/users/octocat/repos",
  "events_url":"https://api.github.com/users/octocat/events{/privacy}",
  "received_events_url":"https://api.github.com/users/octocat/received_events",
  "type":"User",
  "site_admin":false,
  */
//endregion
/*
  "name":"monalisa octocat",
  "company":"GitHub",
  "blog":"https://github.com/blog",
  "location":"San Francisco",
  "email":"octocat@github.com",
  "hireable":false,
  "bio":"There once was...",
  "public_repos":2,
  "public_gists":1,
  "followers":20,
  "following":0,
  "created_at":"2008-01-14T04:33:35Z",
  "updated_at":"2008-01-14T04:33:35Z"
*/
//endregion
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubUserDetailed extends GithubUser {
  private String name;
  private String company;
  private String blog;
  private String location;
  private String email;
  private Boolean hireable;
  private String bio;

  private Integer publicRepos;
  private Integer publicGists;
  private Integer followers;
  private Integer following;

  private Date createdAt;
  private Date updatedAt;

  @Nullable
  public String getName() {
    return name;
  }

  @Nullable
  public String getEmail() {
    return email;
  }
}
