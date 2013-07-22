/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubFile implements Serializable {
  @NotNull private String filename;

  private int additions;
  private int deletions;
  private int changes;
  @NotNull private String status;

  @NotNull private String rawUrl;
  @NotNull private String blobUrl;
  @NotNull private String patch;

  @NotNull
  public static GithubFile create(@Nullable GithubFileRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.filename == null) throw new JsonException("filename is null");
      if (raw.additions == null) throw new JsonException("additions is null");
      if (raw.deletions == null) throw new JsonException("deletions is null");
      if (raw.changes == null) throw new JsonException("changes is null");
      if (raw.status == null) throw new JsonException("status is null");
      if (raw.rawUrl == null) throw new JsonException("rawUrl is null");
      if (raw.blobUrl == null) throw new JsonException("blobUrl is null");
      if (raw.patch == null) throw new JsonException("patch is null");

      return new GithubFile(raw.filename, raw.additions, raw.deletions, raw.changes, raw.status, raw.rawUrl, raw.blobUrl, raw.patch);
    }
    catch (JsonException e) {
      throw new JsonException("CommitFile parse error", e);
    }
  }

  private GithubFile(@NotNull String filename,
                     int additions,
                     int deletions,
                     int changes,
                     @NotNull String status,
                     @NotNull String rawUrl,
                     @NotNull String blobUrl,
                     @NotNull String patch) {
    this.filename = filename;
    this.additions = additions;
    this.deletions = deletions;
    this.changes = changes;
    this.status = status;
    this.rawUrl = rawUrl;
    this.blobUrl = blobUrl;
    this.patch = patch;
  }

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
  public String getBlobUrl() {
    return blobUrl;
  }

  @NotNull
  public String getPatch() {
    return patch;
  }
}
