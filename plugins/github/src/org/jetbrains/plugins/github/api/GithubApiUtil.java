/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.gson.*;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.NullCheckingFactory;
import org.jetbrains.plugins.github.api.data.GithubGist;
import org.jetbrains.plugins.github.api.data.GithubIssue;
import org.jetbrains.plugins.github.exceptions.GithubConfusingException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;

import java.io.IOException;

public class GithubApiUtil {

  private static final Header ACCEPT_V3_JSON = new BasicHeader("Accept", "application/vnd.github.v3+json");

  @NotNull private static final Gson gson = initGson();

  private static Gson initGson() {
    GsonBuilder builder = new GsonBuilder();
    builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
    builder.registerTypeAdapterFactory(NullCheckingFactory.INSTANCE);
    return builder.create();
  }

  @Deprecated
  @NotNull
  public static <T> T fromJson(@Nullable JsonElement json, @NotNull Class<T> classT) throws IOException {
    if (json == null) {
      throw new GithubJsonException("Unexpected empty response");
    }

    try {
      T res = gson.fromJson(json, classT);
      if (res == null) throw new GithubJsonException("Empty Json response");
      return res;
    }
    catch (ClassCastException | JsonParseException e) {
      throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
    }
  }

  @Deprecated
  @NotNull
  private static <T> T load(@NotNull GithubConnection connection,
                            @NotNull String path,
                            @NotNull Class<? extends T> type,
                            @NotNull Header... headers) throws IOException {
    JsonElement result = connection.getRequest(path, headers);
    return fromJson(result, type);
  }

  /**
   * @deprecated use {@link GithubApi.Requests.Gists#get(GithubServerPath, String)} with {@link GithubApiRequestExecutor}
   */
  @Deprecated
  @NotNull
  public static GithubGist getGist(@NotNull GithubConnection connection, @NotNull String id) throws IOException {
    try {
      String path = "/gists/" + id;
      return load(connection, path, GithubGist.class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get gist info: id " + id);
      throw e;
    }
  }

  /**
   * @deprecated use {@link GithubApiRequests.Repos.Issues#get(GithubServerPath, String, String, String)} with {@link GithubApiRequestExecutor}
   */
  @Deprecated
  @NotNull
  public static GithubIssue getIssue(@NotNull GithubConnection connection, @NotNull String user, @NotNull String repo, @NotNull String id)
    throws IOException {
    try {
      String path = "/repos/" + user + "/" + repo + "/issues/" + id;
      return load(connection, path, GithubIssue.class, ACCEPT_V3_JSON);
    }
    catch (GithubConfusingException e) {
      e.setDetails("Can't get issue info: " + user + "/" + repo + " - " + id);
      throw e;
    }
  }
}
