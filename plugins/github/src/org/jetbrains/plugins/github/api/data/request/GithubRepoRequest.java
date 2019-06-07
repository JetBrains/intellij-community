// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubRepoRequest {
  @NotNull private final String name;
  @NotNull private final String description;

  @JsonProperty("private")
  private final boolean isPrivate;
  @Nullable private final Boolean autoInit;

  public GithubRepoRequest(@NotNull String name, @NotNull String description, boolean isPrivate, @Nullable Boolean autoInit) {
    this.name = name;
    this.description = description;
    this.isPrivate = isPrivate;
    this.autoInit = autoInit;
  }
}
