// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

//https://developer.github.com/v3/users/
//region GithubAuthenticatedUser
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
/*
  "total_private_repos": 100,
  "owned_private_repos": 100,
  "private_gists": 81,
  "disk_usage": 10000,
  "collaborators": 8,
  "two_factor_authentication": true,
  "plan": {
      "name": "Medium",
      "space": 400,
      "private_repos": 20,
      "collaborators": 0
  }
*/
//endregion
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubAuthenticatedUser extends GithubUserDetailed {
  private Integer totalPrivateRepos;
  private Integer ownedPrivateRepos;
  private Integer privateGists;
  private Long diskUsage;
  private Integer collaborators;
  private Boolean twoFactorAuthentication;
  private UserPlan plan;

  @RestModel
  static class UserPlan {
    private String name;
    private Long space;
    @Mandatory private Long privateRepos;
    private Long collaborators;
  }

  public boolean canCreatePrivateRepo() {
    return plan == null || ownedPrivateRepos == null || plan.privateRepos > ownedPrivateRepos;
  }
}
