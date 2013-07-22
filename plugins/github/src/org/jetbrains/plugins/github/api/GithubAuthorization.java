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
@SuppressWarnings("UnusedDeclaration")
public class GithubAuthorization {
  private long id;
  @NotNull private String myToken;

  @Nullable private String myNote;
  @Nullable private String myNoteUrl;

  @NotNull private List<String> myScopes;

  @NotNull
  public static GithubAuthorization create(GithubAuthorizationRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.id == null) throw new JsonException("id is null");
      if (raw.token == null) throw new JsonException("token is null");
      if (raw.scopes == null) throw new JsonException("scopes is null");

      return new GithubAuthorization(raw.id, raw.token, raw.note, raw.noteUrl, raw.scopes);
    }
    catch (JsonException e) {
      throw new JsonException("GithubAuthorization parse error", e);
    }
  }

  private GithubAuthorization(long id,
                              @NotNull String token,
                              @Nullable String note,
                              @Nullable String noteUrl,
                              @NotNull List<String> scopes) {
    this.id = id;
    this.myToken = token;
    this.myNote = note;
    this.myNoteUrl = noteUrl;
    this.myScopes = scopes;
  }

  public long getId() {
    return id;
  }

  @NotNull
  public String getToken() {
    return myToken;
  }

  @Nullable
  public String getNote() {
    return myNote;
  }

  @Nullable
  public String getNoteUrl() {
    return myNoteUrl;
  }

  @NotNull
  public List<String> getScopes() {
    return myScopes;
  }
}
