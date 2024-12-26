// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public class GithubAuthorization {
  private Long id;
  private String url;
  private String token;
  private String note;
  private String noteUrl;
  private List<String> scopes;

  public @NotNull String getToken() {
    return token;
  }

  public @NotNull List<String> getScopes() {
    return scopes;
  }

  public @Nullable String getNote() {
    return note;
  }

  public long getId() {
    return id;
  }
}
