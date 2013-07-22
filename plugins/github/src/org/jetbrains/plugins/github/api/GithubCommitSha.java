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
public class GithubCommitSha {
  @NotNull private String myUrl;
  @NotNull private String mySha;

  @NotNull
  public static GithubCommitSha createSha(@Nullable GithubCommitRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.url == null) throw new JsonException("url is null");
      if (raw.sha == null) throw new JsonException("sha is null");

      return new GithubCommitSha(raw.url, raw.sha);
    }
    catch (JsonException e) {
      throw new JsonException("GithubCommitSha parse error", e);
    }
  }

  protected GithubCommitSha(@NotNull String url, @NotNull String sha) {
    this.myUrl = url;
    this.mySha = sha;
  }

  protected GithubCommitSha(@NotNull GithubCommitSha sha) {
    this.myUrl = sha.getUrl();
    this.mySha = sha.getSha();
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getSha() {
    return mySha;
  }
}
