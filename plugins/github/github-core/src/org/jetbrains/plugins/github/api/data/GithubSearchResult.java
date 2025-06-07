// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public class GithubSearchResult<T> {
  private List<T> items;
  private Integer totalCount;
  private Boolean incompleteResults;

  public @NotNull List<T> getItems() {
    return items;
  }
}
