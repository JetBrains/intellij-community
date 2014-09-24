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
package git4idea.push;

import com.intellij.history.Label;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Combined push result for all affected repositories in the project.
 */
class GitPushResult {

  @NotNull private final Map<GitRepository, GitPushRepoResult> myResults;
  @NotNull private final UpdatedFiles myUpdatedFiles;
  @Nullable private final Label myBeforeUpdateLabel;
  @Nullable private final Label myAfterUpdateLabel;

  GitPushResult(@NotNull Map<GitRepository, GitPushRepoResult> results,
                @NotNull UpdatedFiles files,
                @Nullable Label beforeUpdateLabel,
                @Nullable Label afterUpdateLabel) {
    myResults = results;
    myUpdatedFiles = files;
    myBeforeUpdateLabel = beforeUpdateLabel;
    myAfterUpdateLabel = afterUpdateLabel;
  }

  @NotNull
  public Map<GitRepository, GitPushRepoResult> getResults() {
    return myResults;
  }

  @NotNull
  public UpdatedFiles getUpdatedFiles() {
    return myUpdatedFiles;
  }

  @Nullable
  public Label getBeforeUpdateLabel() {
    return myBeforeUpdateLabel;
  }

  @Nullable
  public Label getAfterUpdateLabel() {
    return myAfterUpdateLabel;
  }
}
