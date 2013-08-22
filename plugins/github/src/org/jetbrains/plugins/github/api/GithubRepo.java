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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Aleksey Pivovarov
 */
public class GithubRepo {
  @NotNull private final String myName;
  @NotNull private final String myDescription;

  private final boolean myIsPrivate;
  private final boolean myIsFork;

  @NotNull private final String myHtmlUrl;
  @NotNull private final String myCloneUrl;

  @Nullable private final String myDefaultBranch;

  @NotNull private final GithubUser myOwner;

  public GithubRepo(@NotNull String name,
                    @Nullable String description,
                    boolean isPrivate,
                    boolean isFork,
                    @NotNull String htmlUrl,
                    @NotNull String cloneUrl,
                    @Nullable String defaultBranch,
                    @NotNull GithubUser owner) {
    myName = name;
    myDescription = StringUtil.notNullize(description);
    myIsPrivate = isPrivate;
    myIsFork = isFork;
    myHtmlUrl = htmlUrl;
    myCloneUrl = cloneUrl;
    myDefaultBranch = defaultBranch;
    myOwner = owner;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getFullName() {
    return getUserName() + "/" + getName();
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

  @NotNull
  public String getUserName() {
    return getOwner().getLogin();
  }

  @NotNull
  public GithubFullPath getFullPath() {
    return new GithubFullPath(getUserName(), getName());
  }
}

