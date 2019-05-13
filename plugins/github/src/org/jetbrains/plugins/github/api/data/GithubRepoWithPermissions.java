// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

//example/GithubRepoWithPermissions.json
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubRepoWithPermissions extends GithubRepo {
  @Mandatory private Permissions permissions;

  @NotNull
  public Permissions getPermissions() {
    return permissions;
  }

  @RestModel
  public static class Permissions {
    @Mandatory private Boolean admin;
    @Mandatory private Boolean pull;
    @Mandatory private Boolean push;

    public boolean isAdmin() {
      return admin;
    }

    public boolean isPull() {
      return pull;
    }

    public boolean isPush() {
      return push;
    }
  }
}
