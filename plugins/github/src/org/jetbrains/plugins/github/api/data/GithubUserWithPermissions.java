// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnusedDeclaration")
public class GithubUserWithPermissions extends GithubUser {
  private GithubPermissions permissions;

  @NotNull
  public GithubPermissions getPermissions() {
    return permissions;
  }
}
