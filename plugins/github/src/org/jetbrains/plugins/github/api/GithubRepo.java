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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubRepo {
  private long myId;
  @NotNull private String myName;
  @NotNull private String myFullName;
  @NotNull private String myDescription;

  private boolean myIsPrivate;
  private boolean myIsFork;

  @NotNull private String myUrl;
  @NotNull private String myHtmlUrl;
  @NotNull private String myCloneUrl;
  @NotNull private String myGitUrl;
  @NotNull private String mySshUrl;
  @Nullable private String myMirrorUrl;

  private int mySize;
  @Nullable private String myMasterBranch;

  @NotNull private GithubUser myOwner;

  @NotNull
  public static GithubRepo create(@Nullable GithubRepoRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.id == null) throw new JsonException("id is null");
      if (raw.name == null) throw new JsonException("name is null");
      if (raw.fullName == null) throw new JsonException("fullName is null");
      if (raw.description == null) throw new JsonException("description is null");
      if (raw.isPrivate == null) throw new JsonException("isPrivate is null");
      if (raw.isFork == null) throw new JsonException("isFork is null");
      if (raw.url == null) throw new JsonException("url is null");
      if (raw.htmlUrl == null) throw new JsonException("htmlUrl is null");
      if (raw.cloneUrl == null) throw new JsonException("cloneUrl is null");
      if (raw.gitUrl == null) throw new JsonException("gitUrl is null");
      if (raw.sshUrl == null) throw new JsonException("sshUrl is null");
      if (raw.size == null) throw new JsonException("size is null");
      if (raw.owner == null) throw new JsonException("owner is null");

      GithubUser user = GithubUser.create(raw.owner);

      return new GithubRepo(raw.id, raw.name, raw.fullName, raw.description, raw.isPrivate, raw.isFork, raw.url, raw.htmlUrl, raw.cloneUrl,
                            raw.gitUrl, raw.sshUrl, raw.mirrorUrl, raw.size, raw.masterBranch, user);
    }
    catch (JsonException e) {
      throw new JsonException("GithubRepo parse error", e);
    }
  }

  protected GithubRepo(long id,
                       @NotNull String name,
                       @NotNull String fullName,
                       @NotNull String description,
                       boolean aPrivate,
                       boolean fork,
                       @NotNull String url,
                       @NotNull String htmlUrl,
                       @NotNull String cloneUrl,
                       @NotNull String gitUrl,
                       @NotNull String sshUrl,
                       @Nullable String mirrorUrl,
                       int size,
                       @Nullable String masterBranch,
                       @NotNull GithubUser owner) {
    this.myId = id;
    this.myName = name;
    this.myFullName = fullName;
    this.myDescription = description;
    myIsPrivate = aPrivate;
    myIsFork = fork;
    this.myUrl = url;
    this.myHtmlUrl = htmlUrl;
    this.myCloneUrl = cloneUrl;
    this.myGitUrl = gitUrl;
    this.mySshUrl = sshUrl;
    this.myMirrorUrl = mirrorUrl;
    this.mySize = size;
    this.myMasterBranch = masterBranch;
    this.myOwner = owner;
  }

  protected GithubRepo(@NotNull GithubRepo repo) {
    this(repo.myId, repo.myName, repo.myFullName, repo.myDescription, repo.myIsPrivate, repo.myIsFork, repo.myUrl, repo.myHtmlUrl, repo.myCloneUrl,
         repo.myGitUrl, repo.mySshUrl, repo.myMirrorUrl, repo.mySize, repo.myMasterBranch, repo.myOwner);
  }

  public long getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getFullName() {
    return myFullName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public boolean isPrivate() {
    return myIsPrivate;
  }

  public boolean isFork() {
    return myIsFork;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @NotNull
  public String getCloneUrl() {
    return myCloneUrl;
  }

  @NotNull
  public String getGitUrl() {
    return myGitUrl;
  }

  @NotNull
  public String getSshUrl() {
    return mySshUrl;
  }

  @Nullable
  public String getMirrorUrl() {
    return myMirrorUrl;
  }

  public int getSize() {
    return mySize;
  }

  @Nullable
  public String getMasterBranch() {
    return myMasterBranch;
  }

  @NotNull
  public GithubUser getOwner() {
    return myOwner;
  }
}

