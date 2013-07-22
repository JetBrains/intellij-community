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

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubRepoDetailed extends GithubRepo {
  @Nullable private GithubRepo myParent;
  @Nullable private GithubRepo mySource;

  private boolean myHasIssues;
  private boolean myHasWiki;
  private boolean myHasDownloads;

  @NotNull
  public static GithubRepoDetailed createDetailed(@Nullable GithubRepoRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.hasIssues == null) throw new JsonException("hasIssues is null");
      if (raw.hasWiki == null) throw new JsonException("hasWiki is null");
      if (raw.hasDownloads == null) throw new JsonException("hasDownloads is null");

      GithubRepo repo = GithubRepo.create(raw);
      GithubRepo parent = raw.parent == null ? null : GithubRepo.create(raw.parent);
      GithubRepo source = raw.source == null ? null : GithubRepo.create(raw.source);

      return new GithubRepoDetailed(repo, parent, source, raw.hasIssues, raw.hasWiki,
                                    raw.hasDownloads);
    }
    catch (JsonException e) {
      throw new JsonException("GithubRepoDetailed parse error", e);
    }
  }

  private GithubRepoDetailed(@NotNull GithubRepo repo,
                               @Nullable GithubRepo parent,
                               @Nullable GithubRepo source,
                               boolean hasIssues,
                               boolean hasWiki,
                               boolean hasDownloads) {
    super(repo);
    this.myParent = parent;
    this.mySource = source;
    this.myHasIssues = hasIssues;
    this.myHasWiki = hasWiki;
    this.myHasDownloads = hasDownloads;
  }

  @Nullable
  public GithubRepo getParent() {
    return myParent;
  }

  @Nullable
  public GithubRepo getSource() {
    return mySource;
  }

  public boolean hasIssues() {
    return myHasIssues;
  }

  public boolean hasWiki() {
    return myHasWiki;
  }

  public boolean hasDownloads() {
    return myHasDownloads;
  }
}
