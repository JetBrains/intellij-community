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
public class GithubRepo {
  @NotNull private String myName;
  @NotNull private String myFullName;
  @NotNull private String myDescription;

  private boolean myIsPrivate;
  private boolean myIsFork;

  @NotNull private String myHtmlUrl;
  @NotNull private String myCloneUrl;

  @Nullable private String myDefaultBranch;

  @NotNull private GithubUser myOwner;

  @NotNull
  @SuppressWarnings("ConstantConditions")
  public static GithubRepo create(@Nullable GithubRepoRaw raw) throws JsonException {
    try {
      return new GithubRepo(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubRepo parse error", e);
    }
    catch (JsonException e) {
      throw new JsonException("GithubRepo parse error", e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected GithubRepo(@NotNull GithubRepoRaw raw) throws JsonException {
    myName = raw.name;
    myFullName = raw.fullName;
    myDescription = raw.description;
    myIsPrivate = raw.isPrivate;
    myIsFork = raw.isFork;
    myHtmlUrl = raw.htmlUrl;
    myCloneUrl = raw.cloneUrl;
    myDefaultBranch = raw.defaultBranch;
    myOwner = GithubUser.create(raw.owner);
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
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @NotNull
  public String getCloneUrl() {
    return myCloneUrl;
  }

  @Nullable
  public String getDefaultBranch() {
    return myDefaultBranch;
  }

  @NotNull
  public GithubUser getOwner() {
    return myOwner;
  }
}

