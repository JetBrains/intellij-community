// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//region issues api model
/*
{
  "url": "https://api.github.com/repos/iasemenov/testing/issues/6",
  "repository_url": "https://api.github.com/repos/iasemenov/testing",
  "labels_url": "https://api.github.com/repos/iasemenov/testing/issues/6/labels{/name}",
  "comments_url": "https://api.github.com/repos/iasemenov/testing/issues/6/comments",
  "events_url": "https://api.github.com/repos/iasemenov/testing/issues/6/events",
  "html_url": "https://github.com/iasemenov/testing/issues/6",
  "id": 343648850,
  "node_id": "MDU6SXNzdWUzNDM2NDg4NTA=",
  "number": 6,
  "title": "test3",
  "user": {
    "login": "iasemenov",
    "id": 33097396,
    "node_id": "MDQ6VXNlcjMzMDk3Mzk2",
    "avatar_url": "https://avatars2.githubusercontent.com/u/33097396?v=4",
    "gravatar_id": "",
    "url": "https://api.github.com/users/iasemenov",
    "html_url": "https://github.com/iasemenov",
    "followers_url": "https://api.github.com/users/iasemenov/followers",
    "following_url": "https://api.github.com/users/iasemenov/following{/other_user}",
    "gists_url": "https://api.github.com/users/iasemenov/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/iasemenov/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/iasemenov/subscriptions",
    "organizations_url": "https://api.github.com/users/iasemenov/orgs",
    "repos_url": "https://api.github.com/users/iasemenov/repos",
    "events_url": "https://api.github.com/users/iasemenov/events{/privacy}",
    "received_events_url": "https://api.github.com/users/iasemenov/received_events",
    "type": "User",
    "site_admin": false
  },
  "labels": [],
  "state": "closed",
  "locked": false,
  "assignee": {
    "login": "iasemenov",
    "id": 33097396,
    "node_id": "MDQ6VXNlcjMzMDk3Mzk2",
    "avatar_url": "https://avatars2.githubusercontent.com/u/33097396?v=4",
    "gravatar_id": "",
    "url": "https://api.github.com/users/iasemenov",
    "html_url": "https://github.com/iasemenov",
    "followers_url": "https://api.github.com/users/iasemenov/followers",
    "following_url": "https://api.github.com/users/iasemenov/following{/other_user}",
    "gists_url": "https://api.github.com/users/iasemenov/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/iasemenov/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/iasemenov/subscriptions",
    "organizations_url": "https://api.github.com/users/iasemenov/orgs",
    "repos_url": "https://api.github.com/users/iasemenov/repos",
    "events_url": "https://api.github.com/users/iasemenov/events{/privacy}",
    "received_events_url": "https://api.github.com/users/iasemenov/received_events",
    "type": "User",
    "site_admin": false
  },
  "assignees": [
    {
      "login": "iasemenov",
      "id": 33097396,
      "node_id": "MDQ6VXNlcjMzMDk3Mzk2",
      "avatar_url": "https://avatars2.githubusercontent.com/u/33097396?v=4",
      "gravatar_id": "",
      "url": "https://api.github.com/users/iasemenov",
      "html_url": "https://github.com/iasemenov",
      "followers_url": "https://api.github.com/users/iasemenov/followers",
      "following_url": "https://api.github.com/users/iasemenov/following{/other_user}",
      "gists_url": "https://api.github.com/users/iasemenov/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/iasemenov/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/iasemenov/subscriptions",
      "organizations_url": "https://api.github.com/users/iasemenov/orgs",
      "repos_url": "https://api.github.com/users/iasemenov/repos",
      "events_url": "https://api.github.com/users/iasemenov/events{/privacy}",
      "received_events_url": "https://api.github.com/users/iasemenov/received_events",
      "type": "User",
      "site_admin": false
    }
  ],
  "milestone": null,
  "comments": 0,
  "created_at": "2018-07-23T14:07:48Z",
  "updated_at": "2018-07-23T17:05:21Z",
  "closed_at": "2018-07-23T17:05:21Z",
  "author_association": "OWNER",
  "pull_request": {
    "url": "https://api.github.com/repos/iasemenov/testing/pulls/8",
    "html_url": "https://github.com/iasemenov/testing/pull/8",
    "diff_url": "https://github.com/iasemenov/testing/pull/8.diff",
    "patch_url": "https://github.com/iasemenov/testing/pull/8.patch"
  },
  "body": "",
  "score": 1
}*/
//endregion
@SuppressWarnings("UnusedDeclaration")
public class GithubSearchedIssue extends GithubIssueBase {
  private PullRequestLinks pullRequest;

  public @Nullable PullRequestLinks getPullRequestLinks() {
    return pullRequest;
  }

  public static class PullRequestLinks {
    private String url;
    //non-api urls
    private String htmlUrl;
    private String diffUrl;
    private String patchUrl;

    public @NotNull String getUrl() {
      return url;
    }

    public @NotNull String getHtmlUrl() {
      return htmlUrl;
    }

    public @NotNull String getPatchUrl() {
      return patchUrl;
    }
  }
}
