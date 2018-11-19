/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.io.mandatory.RestModel;

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
  "body": "",
  "closed_by": {
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
}*/
//endregion
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubIssue extends GithubIssueBase {
  private GithubUser closedBy;
}
