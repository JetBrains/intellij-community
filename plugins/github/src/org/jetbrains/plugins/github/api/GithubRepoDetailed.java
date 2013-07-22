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
public class GithubRepoDetailed extends GithubRepo {
  @Nullable private GithubRepo myParent;

  @NotNull
  @SuppressWarnings("ConstantConditions")
  public static GithubRepoDetailed createDetailed(@Nullable GithubRepoRaw raw) throws JsonException {
    try {
      return new GithubRepoDetailed(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubRepoDetailed parse error", e);
    }
    catch (JsonException e) {
      throw new JsonException("GithubRepoDetailed parse error", e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected GithubRepoDetailed(@NotNull GithubRepoRaw raw) throws JsonException {
    super(raw);
    myParent = raw.parent == null ? null : GithubRepo.create(raw.parent);
  }

  @Nullable
  public GithubRepo getParent() {
    return myParent;
  }

}
