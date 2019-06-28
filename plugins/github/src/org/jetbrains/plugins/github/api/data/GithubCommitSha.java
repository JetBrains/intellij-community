// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnusedDeclaration")
public class GithubCommitSha {
  private String url;
  private String sha;

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getSha() {
    return sha;
  }
}
