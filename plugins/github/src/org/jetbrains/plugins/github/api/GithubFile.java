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
  @NotNull private String myFilename;

  private int myAdditions;
  private int myDeletions;
  private int myChanges;
  @NotNull private String myStatus;

  @NotNull private String myRawUrl;
  @NotNull private String myBlobUrl;
  @NotNull private String myPatch;

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
    this.myFilename = filename;
    this.myAdditions = additions;
    this.myDeletions = deletions;
    this.myChanges = changes;
    this.myStatus = status;
    this.myRawUrl = rawUrl;
    this.myBlobUrl = blobUrl;
    this.myPatch = patch;
  }

  @NotNull
  public String getFilename() {
    return myFilename;
  }

  public int getAdditions() {
    return myAdditions;
  }

  public int getDeletions() {
    return myDeletions;
  }

  public int getChanges() {
    return myChanges;
  }

  @NotNull
  public String getStatus() {
    return myStatus;
  }

  @NotNull
  public String getRawUrl() {
    return myRawUrl;
  }

  @NotNull
  public String getBlobUrl() {
    return myBlobUrl;
  }

  @NotNull
  public String getPatch() {
    return myPatch;
  }
}
