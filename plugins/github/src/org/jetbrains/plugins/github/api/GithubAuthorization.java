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

import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubAuthorization {
  @NotNull private String myToken;
  @NotNull private List<String> myScopes;

  @NotNull
  @SuppressWarnings("ConstantConditions")
  public static GithubAuthorization create(@Nullable GithubAuthorizationRaw raw) throws JsonException {
    try {
      return new GithubAuthorization(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubAuthorization parse error", e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected GithubAuthorization(@NotNull GithubAuthorizationRaw raw) {
    this(raw.token, raw.scopes);
  }

  private GithubAuthorization(@NotNull String token, @NotNull List<String> scopes) {
    myToken = token;
    myScopes = scopes;
  }

  @NotNull
  public String getToken() {
    return myToken;
  }

  @NotNull
  public List<String> getScopes() {
    return myScopes;
  }
}
