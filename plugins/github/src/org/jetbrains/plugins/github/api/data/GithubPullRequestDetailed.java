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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequestDetailed extends GithubPullRequest {
  private Boolean merged;
  private Boolean mergeable;

  private Integer comments;
  private Integer commits;
  private Integer additions;
  private Integer deletions;
  private Integer changedFiles;

  private String url;
  @Mandatory private String htmlUrl;
  @Mandatory private String diffUrl;
  @Mandatory private String patchUrl;
  @Mandatory private String issueUrl;

  @Mandatory private Link head;
  @Mandatory private Link base;

  @RestModel
  public static class Link {
    @Mandatory private String label;
    @Mandatory private String ref;
    @Mandatory private String sha;

    private GithubRepo repo;
    private GithubUser user;

    @NotNull
    public String getLabel() {
      return label;
    }

    @NotNull
    public String getRef() {
      return ref;
    }

    @NotNull
    public String getSha() {
      return sha;
    }

    @Nullable
    public GithubRepo getRepo() {
      return repo;
    }

    @Nullable
    public GithubUser getUser() {
      return user;
    }
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  @NotNull
  public String getDiffUrl() {
    return diffUrl;
  }

  @NotNull
  public String getPatchUrl() {
    return patchUrl;
  }

  @NotNull
  public String getIssueUrl() {
    return issueUrl;
  }

  @NotNull
  public Link getHead() {
    return head;
  }

  @NotNull
  public Link getBase() {
    return base;
  }
}
