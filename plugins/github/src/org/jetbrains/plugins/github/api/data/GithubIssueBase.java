// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

import java.util.Date;
import java.util.List;
import java.util.Objects;

//region Base for issues api model and search issues/pr api model
/*{
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
  "body": ""
}*/
//endregion
@RestModel
@SuppressWarnings("UnusedDeclaration")
public abstract class GithubIssueBase {
  private String url;
  private String repositoryUrl;
  private String labelsUrl;
  @Mandatory private String commentsUrl;
  private String eventsUrl;
  @Mandatory private String htmlUrl;
  private Long id;
  private String nodeId;
  @Mandatory private Long number;
  @Mandatory private String title;
  @Mandatory private GithubUser user;
  private List<GithubIssueLabel> labels;
  @Mandatory private GithubIssueState state;
  @Mandatory private Boolean locked;
  private GithubUser assignee;
  @Mandatory private List<GithubUser> assignees;
  //private ??? milestone;
  private Long comments;
  @Mandatory private Date createdAt;
  @Mandatory private Date updatedAt;
  private Date closedAt;
  private String authorAssociation;
  private String body;

  @NotNull
  public String getCommentsUrl() {
    return commentsUrl;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  public long getNumber() {
    return number;
  }

  @NotNull
  public GithubIssueState getState() {
    return state;
  }

  @NotNull
  public String getTitle() {
    return title;
  }

  @Nullable
  public List<GithubIssueLabel> getLabels() {
    return labels;
  }

  @NotNull
  public String getBody() {
    return StringUtil.notNullize(body);
  }

  @NotNull
  public GithubUser getUser() {
    return user;
  }

  @Nullable
  public GithubUser getAssignee() {
    return assignee;
  }

  @NotNull
  public List<GithubUser> getAssignees() {
    return assignees;
  }

  @Nullable
  public Date getClosedAt() {
    return closedAt;
  }

  @NotNull
  public Date getCreatedAt() {
    return createdAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubIssueBase)) return false;
    GithubIssueBase base = (GithubIssueBase)o;
    return number.equals(base.number);
  }

  @Override
  public int hashCode() {
    return Objects.hash(number);
  }
}
