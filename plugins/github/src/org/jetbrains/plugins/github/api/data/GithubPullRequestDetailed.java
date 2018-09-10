// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.io.mandatory.RestModel;

//https://developer.github.com/v3/pulls/
//region GithubPullRequestDetailed
//region GithubPullRequest
/*
"url": "https://api.github.com/repos/iasemenov/testing/pulls/1",
"id": 180865260,
"html_url": "https://github.com/iasemenov/testing/pull/1",
"diff_url": "https://github.com/iasemenov/testing/pull/1.diff",
"patch_url": "https://github.com/iasemenov/testing/pull/1.patch",
"issue_url": "https://api.github.com/repos/iasemenov/testing/issues/1",
"number": 1,
"state": "open",
"locked": false,
"title": "pr_test",
"user": {
  "login": "iasemenov",
  "id": 33097396,
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
"body": "test",
"created_at": "2018-04-11T10:58:46Z",
"updated_at": "2018-04-11T11:09:36Z",
"closed_at": null,
"merged_at": null,
"merge_commit_sha": "cd72119c443f477698a25f3a27effa1896d8718a",
"assignee": {
  "login": "iasemenov",
  "id": 33097396,
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
"requested_reviewers": [

],
"requested_teams": [

],
"labels": [

],
"milestone": null,
"commits_url": "https://api.github.com/repos/iasemenov/testing/pulls/1/commits",
"review_comments_url": "https://api.github.com/repos/iasemenov/testing/pulls/1/comments",
"review_comment_url": "https://api.github.com/repos/iasemenov/testing/pulls/comments{/number}",
"comments_url": "https://api.github.com/repos/iasemenov/testing/issues/1/comments",
"statuses_url": "https://api.github.com/repos/iasemenov/testing/statuses/199b084e4e3da958dff2da3d319a27caf34cfc7a",
"head": {
  "label": "iasemenov:pr_test",
  "ref": "pr_test",
  "sha": "199b084e4e3da958dff2da3d319a27caf34cfc7a",
  "user": {
    "login": "iasemenov",
    "id": 33097396,
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
  "repo": {
    "id": 109837424,
    "name": "testing",
    "full_name": "iasemenov/testing",
    "owner": {
      "login": "iasemenov",
      "id": 33097396,
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
    "private": false,
    "html_url": "https://github.com/iasemenov/testing",
    "description": null,
    "fork": false,
    "url": "https://api.github.com/repos/iasemenov/testing",
    "forks_url": "https://api.github.com/repos/iasemenov/testing/forks",
    "keys_url": "https://api.github.com/repos/iasemenov/testing/keys{/key_id}",
    "collaborators_url": "https://api.github.com/repos/iasemenov/testing/collaborators{/collaborator}",
    "teams_url": "https://api.github.com/repos/iasemenov/testing/teams",
    "hooks_url": "https://api.github.com/repos/iasemenov/testing/hooks",
    "issue_events_url": "https://api.github.com/repos/iasemenov/testing/issues/events{/number}",
    "events_url": "https://api.github.com/repos/iasemenov/testing/events",
    "assignees_url": "https://api.github.com/repos/iasemenov/testing/assignees{/user}",
    "branches_url": "https://api.github.com/repos/iasemenov/testing/branches{/branch}",
    "tags_url": "https://api.github.com/repos/iasemenov/testing/tags",
    "blobs_url": "https://api.github.com/repos/iasemenov/testing/git/blobs{/sha}",
    "git_tags_url": "https://api.github.com/repos/iasemenov/testing/git/tags{/sha}",
    "git_refs_url": "https://api.github.com/repos/iasemenov/testing/git/refs{/sha}",
    "trees_url": "https://api.github.com/repos/iasemenov/testing/git/trees{/sha}",
    "statuses_url": "https://api.github.com/repos/iasemenov/testing/statuses/{sha}",
    "languages_url": "https://api.github.com/repos/iasemenov/testing/languages",
    "stargazers_url": "https://api.github.com/repos/iasemenov/testing/stargazers",
    "contributors_url": "https://api.github.com/repos/iasemenov/testing/contributors",
    "subscribers_url": "https://api.github.com/repos/iasemenov/testing/subscribers",
    "subscription_url": "https://api.github.com/repos/iasemenov/testing/subscription",
    "commits_url": "https://api.github.com/repos/iasemenov/testing/commits{/sha}",
    "git_commits_url": "https://api.github.com/repos/iasemenov/testing/git/commits{/sha}",
    "comments_url": "https://api.github.com/repos/iasemenov/testing/comments{/number}",
    "issue_comment_url": "https://api.github.com/repos/iasemenov/testing/issues/comments{/number}",
    "contents_url": "https://api.github.com/repos/iasemenov/testing/contents/{+path}",
    "compare_url": "https://api.github.com/repos/iasemenov/testing/compare/{base}...{head}",
    "merges_url": "https://api.github.com/repos/iasemenov/testing/merges",
    "archive_url": "https://api.github.com/repos/iasemenov/testing/{archive_format}{/ref}",
    "downloads_url": "https://api.github.com/repos/iasemenov/testing/downloads",
    "issues_url": "https://api.github.com/repos/iasemenov/testing/issues{/number}",
    "pulls_url": "https://api.github.com/repos/iasemenov/testing/pulls{/number}",
    "milestones_url": "https://api.github.com/repos/iasemenov/testing/milestones{/number}",
    "notifications_url": "https://api.github.com/repos/iasemenov/testing/notifications{?since,all,participating}",
    "labels_url": "https://api.github.com/repos/iasemenov/testing/labels{/name}",
    "releases_url": "https://api.github.com/repos/iasemenov/testing/releases{/id}",
    "deployments_url": "https://api.github.com/repos/iasemenov/testing/deployments",
    "created_at": "2017-11-07T13:12:21Z",
    "updated_at": "2018-05-25T10:00:17Z",
    "pushed_at": "2018-05-25T10:00:16Z",
    "git_url": "git://github.com/iasemenov/testing.git",
    "ssh_url": "git@github.com:iasemenov/testing.git",
    "clone_url": "https://github.com/iasemenov/testing.git",
    "svn_url": "https://github.com/iasemenov/testing",
    "homepage": null,
    "size": 10,
    "stargazers_count": 0,
    "watchers_count": 0,
    "language": null,
    "has_issues": true,
    "has_projects": true,
    "has_downloads": true,
    "has_wiki": true,
    "has_pages": false,
    "forks_count": 1,
    "mirror_url": null,
    "archived": false,
    "open_issues_count": 1,
    "license": null,
    "forks": 1,
    "open_issues": 1,
    "watchers": 0,
    "default_branch": "master"
  }
},
"base": {
  "label": "iasemenov:master",
  "ref": "master",
  "sha": "9b9a87ff885d880668f9568bbb7c5de0044fcad3",
  "user": {
    "login": "iasemenov",
    "id": 33097396,
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
  "repo": {
    "id": 109837424,
    "name": "testing",
    "full_name": "iasemenov/testing",
    "owner": {
      "login": "iasemenov",
      "id": 33097396,
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
    "private": false,
    "html_url": "https://github.com/iasemenov/testing",
    "description": null,
    "fork": false,
    "url": "https://api.github.com/repos/iasemenov/testing",
    "forks_url": "https://api.github.com/repos/iasemenov/testing/forks",
    "keys_url": "https://api.github.com/repos/iasemenov/testing/keys{/key_id}",
    "collaborators_url": "https://api.github.com/repos/iasemenov/testing/collaborators{/collaborator}",
    "teams_url": "https://api.github.com/repos/iasemenov/testing/teams",
    "hooks_url": "https://api.github.com/repos/iasemenov/testing/hooks",
    "issue_events_url": "https://api.github.com/repos/iasemenov/testing/issues/events{/number}",
    "events_url": "https://api.github.com/repos/iasemenov/testing/events",
    "assignees_url": "https://api.github.com/repos/iasemenov/testing/assignees{/user}",
    "branches_url": "https://api.github.com/repos/iasemenov/testing/branches{/branch}",
    "tags_url": "https://api.github.com/repos/iasemenov/testing/tags",
    "blobs_url": "https://api.github.com/repos/iasemenov/testing/git/blobs{/sha}",
    "git_tags_url": "https://api.github.com/repos/iasemenov/testing/git/tags{/sha}",
    "git_refs_url": "https://api.github.com/repos/iasemenov/testing/git/refs{/sha}",
    "trees_url": "https://api.github.com/repos/iasemenov/testing/git/trees{/sha}",
    "statuses_url": "https://api.github.com/repos/iasemenov/testing/statuses/{sha}",
    "languages_url": "https://api.github.com/repos/iasemenov/testing/languages",
    "stargazers_url": "https://api.github.com/repos/iasemenov/testing/stargazers",
    "contributors_url": "https://api.github.com/repos/iasemenov/testing/contributors",
    "subscribers_url": "https://api.github.com/repos/iasemenov/testing/subscribers",
    "subscription_url": "https://api.github.com/repos/iasemenov/testing/subscription",
    "commits_url": "https://api.github.com/repos/iasemenov/testing/commits{/sha}",
    "git_commits_url": "https://api.github.com/repos/iasemenov/testing/git/commits{/sha}",
    "comments_url": "https://api.github.com/repos/iasemenov/testing/comments{/number}",
    "issue_comment_url": "https://api.github.com/repos/iasemenov/testing/issues/comments{/number}",
    "contents_url": "https://api.github.com/repos/iasemenov/testing/contents/{+path}",
    "compare_url": "https://api.github.com/repos/iasemenov/testing/compare/{base}...{head}",
    "merges_url": "https://api.github.com/repos/iasemenov/testing/merges",
    "archive_url": "https://api.github.com/repos/iasemenov/testing/{archive_format}{/ref}",
    "downloads_url": "https://api.github.com/repos/iasemenov/testing/downloads",
    "issues_url": "https://api.github.com/repos/iasemenov/testing/issues{/number}",
    "pulls_url": "https://api.github.com/repos/iasemenov/testing/pulls{/number}",
    "milestones_url": "https://api.github.com/repos/iasemenov/testing/milestones{/number}",
    "notifications_url": "https://api.github.com/repos/iasemenov/testing/notifications{?since,all,participating}",
    "labels_url": "https://api.github.com/repos/iasemenov/testing/labels{/name}",
    "releases_url": "https://api.github.com/repos/iasemenov/testing/releases{/id}",
    "deployments_url": "https://api.github.com/repos/iasemenov/testing/deployments",
    "created_at": "2017-11-07T13:12:21Z",
    "updated_at": "2018-05-25T10:00:17Z",
    "pushed_at": "2018-05-25T10:00:16Z",
    "git_url": "git://github.com/iasemenov/testing.git",
    "ssh_url": "git@github.com:iasemenov/testing.git",
    "clone_url": "https://github.com/iasemenov/testing.git",
    "svn_url": "https://github.com/iasemenov/testing",
    "homepage": null,
    "size": 10,
    "stargazers_count": 0,
    "watchers_count": 0,
    "language": null,
    "has_issues": true,
    "has_projects": true,
    "has_downloads": true,
    "has_wiki": true,
    "has_pages": false,
    "forks_count": 1,
    "mirror_url": null,
    "archived": false,
    "open_issues_count": 1,
    "license": null,
    "forks": 1,
    "open_issues": 1,
    "watchers": 0,
    "default_branch": "master"
  }
},
"_links": {
  "self": {
    "href": "https://api.github.com/repos/iasemenov/testing/pulls/1"
  },
  "html": {
    "href": "https://github.com/iasemenov/testing/pull/1"
  },
  "issue": {
    "href": "https://api.github.com/repos/iasemenov/testing/issues/1"
  },
  "comments": {
    "href": "https://api.github.com/repos/iasemenov/testing/issues/1/comments"
  },
  "review_comments": {
    "href": "https://api.github.com/repos/iasemenov/testing/pulls/1/comments"
  },
  "review_comment": {
    "href": "https://api.github.com/repos/iasemenov/testing/pulls/comments{/number}"
  },
  "commits": {
    "href": "https://api.github.com/repos/iasemenov/testing/pulls/1/commits"
  },
  "statuses": {
    "href": "https://api.github.com/repos/iasemenov/testing/statuses/199b084e4e3da958dff2da3d319a27caf34cfc7a"
  }
},
"author_association": "OWNER"
*/
//endregion
/*
"merged": false,
"mergeable": false,
"rebaseable": false,
"mergeable_state": "dirty",
"merged_by": null,
"comments": 1,
"review_comments": 1,
"maintainer_can_modify": false,
"commits": 2,
"additions": 3,
"deletions": 1,
"changed_files": 2
*/
//endregion
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequestDetailed extends GithubPullRequest {
  private Boolean merged;
  private Boolean mergeable;
  private Boolean rebaseable;
  private String mergeableState;
  private GithubUser mergedBy;

  private Integer comments;
  private Integer reviewComments;
  private Boolean maintainerCanModify;
  private Integer commits;
  private Integer additions;
  private Integer deletions;
  private Integer changedFiles;
}
