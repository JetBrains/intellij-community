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

import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubUtil;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubRepoDetailed extends GithubRepo {
  @Nullable private GithubRepo parent;
  @Nullable private GithubRepo source;

  private boolean hasIssues;
  private boolean hasWiki;
  private boolean hasDownloads;

  @Nullable
  public static GithubRepoDetailed createDetailed(@Nullable GithubRepoRaw raw) {
    try {
      if (raw == null) throw new JsonParseException("raw is null");
      if (raw.hasIssues == null) throw new JsonParseException("hasIssues is null");
      if (raw.hasWiki == null) throw new JsonParseException("hasWiki is null");
      if (raw.hasDownloads == null) throw new JsonParseException("hasDownloads is null");

      GithubRepo repo = GithubRepo.create(raw);
      if (repo == null) throw new JsonParseException("repo is null");

      return new GithubRepoDetailed(repo, GithubRepo.create(raw.parent), GithubRepo.create(raw.source), raw.hasIssues, raw.hasWiki,
                                    raw.hasDownloads);
    }
    catch (JsonParseException e) {
      GithubUtil.LOG.info("GithubRepoDetailed parse error: " + e.getMessage());
      return null;
    }
  }

  private GithubRepoDetailed(@NotNull GithubRepo repo,
                               @Nullable GithubRepo parent,
                               @Nullable GithubRepo source,
                               boolean hasIssues,
                               boolean hasWiki,
                               boolean hasDownloads) {
    super(repo);
    this.parent = parent;
    this.source = source;
    this.hasIssues = hasIssues;
    this.hasWiki = hasWiki;
    this.hasDownloads = hasDownloads;
  }

  @Nullable
  public GithubRepo getParent() {
    return parent;
  }

  @Nullable
  public GithubRepo getSource() {
    return source;
  }

  public boolean isHasIssues() {
    return hasIssues;
  }

  public boolean isHasWiki() {
    return hasWiki;
  }

  public boolean isHasDownloads() {
    return hasDownloads;
  }
}
