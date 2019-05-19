// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public String getToken() {
    return token;
  }

  @NotNull
  public List<String> getScopes() {
    return scopes;
  }

  @Nullable
  public String getNote() {
    return note;
  }

  public long getId() {
    return id;
  }
}
