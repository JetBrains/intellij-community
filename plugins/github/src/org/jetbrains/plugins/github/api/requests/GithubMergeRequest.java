/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api.requests;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration", "FieldMayBeStatic"})
public class GithubMergeRequest {
  @SerializedName("commit_title")
  @NotNull private final String commitTitle;
  @SerializedName("commit_message")
  @NotNull private final String commitMessage;
  @NotNull private final String sha;
  @SerializedName("merge_method")
  @NotNull private final String mergeMethod = "merge";

  public GithubMergeRequest(@NotNull String commitTitle, @NotNull String commitMessage, @NotNull String sha) {
    this.commitTitle = commitTitle;
    this.commitMessage = commitMessage;
    this.sha = sha;
  }
}
