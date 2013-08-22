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
@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubRepoOrg extends GithubRepo {
  @NotNull private final Permissions myPermissions;

  public static class Permissions {
    private final boolean myAdmin;
    private final boolean myPull;
    private final boolean myPush;

    public Permissions(boolean admin, boolean pull, boolean push) {
      myAdmin = admin;
      myPull = pull;
      myPush = push;
    }

    public boolean isAdmin() {
      return myAdmin;
    }

    public boolean isPull() {
      return myPull;
    }

    public boolean isPush() {
      return myPush;
    }
  }

  public GithubRepoOrg(@NotNull String name,
                       @Nullable String description,
                       boolean isPrivate,
                       boolean isFork,
                       @NotNull String htmlUrl,
                       @NotNull String cloneUrl,
                       @Nullable String defaultBranch,
                       @NotNull GithubUser owner,
                       @NotNull Permissions permissions) {
    super(name, description, isPrivate, isFork, htmlUrl, cloneUrl, defaultBranch, owner);
    myPermissions = permissions;
  }
}
