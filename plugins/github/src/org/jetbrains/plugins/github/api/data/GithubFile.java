// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnusedDeclaration")
public class GithubFile {
  private String filename;

  private Integer additions;
  private Integer deletions;
  private Integer changes;
  private String status;
  private String rawUrl;
  private String blobUrl;
  private String patch;

  @NotNull
  public String getFilename() {
    return filename;
  }

  public int getAdditions() {
    return additions;
  }

  public int getDeletions() {
    return deletions;
  }

  public int getChanges() {
    return changes;
  }

  @NotNull
  public String getStatus() {
    return status;
  }

  @NotNull
  public String getRawUrl() {
    return rawUrl;
  }

  @NotNull
  public String getPatch() {
    return patch;
  }
}
