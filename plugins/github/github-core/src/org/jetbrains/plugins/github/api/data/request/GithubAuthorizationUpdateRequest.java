// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubAuthorizationUpdateRequest {
  private final @NotNull List<String> addScopes;

  public GithubAuthorizationUpdateRequest(@NotNull List<String> newScopes) {
    this.addScopes = newScopes;
  }
}
