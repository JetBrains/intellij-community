// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubAuthorizationUpdateRequest {
  @NotNull private final List<String> addScopes;

  public GithubAuthorizationUpdateRequest(@NotNull List<String> newScopes) {
    this.addScopes = newScopes;
  }
}
