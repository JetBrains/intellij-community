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
package org.jetbrains.plugins.github;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about Github repository.
 *
 * @author oleg
 * @author Kirill Likhodedov
 */
public class RepositoryInfo {

  @NotNull private final String myName;
  @NotNull private final String myBrowserUrl;
  @NotNull private final String myCloneUrl;
  @NotNull private final String myOwnerName;
  @Nullable private final String myParentName;
  private final boolean myFork;

  public RepositoryInfo(@NotNull String name,
                        @NotNull String browserUrl,
                        @NotNull String cloneUrl,
                        @NotNull String ownerName,
                        @Nullable String parentName,
                        boolean fork) {
    myName = name;
    myBrowserUrl = browserUrl;
    myCloneUrl = cloneUrl;
    myOwnerName = ownerName;
    myParentName = parentName;
    myFork = fork;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getOwnerName() {
    return myOwnerName;
  }

  public boolean isFork() {
    return myFork;
  }

  /**
   * @return The name of the parent of this repository, or null.
   *         Null is returned if this repository doesn't have a parent, i. e. is not a fork,
   *         or if the parent information was not retrieved by the time of constructing of this RepositoryInfo object.
   *         To be sure use {@link #isFork()}.
   */
  @Nullable
  public String getParentName() {
    return myParentName;
  }

  @NotNull
  public String getCloneUrl() {
    return myCloneUrl;
  }

  @NotNull
  public String getBrowserUrl() {
    return myBrowserUrl;
  }
}
