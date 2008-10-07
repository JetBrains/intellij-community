package git4idea;
/*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at:
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
* the specific language governing permissions and limitations under the License.
*
* Copyright 2008 MQSoftware
* Authors: Mark Scott
*/

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Git update environment implementation
 */
public class GitUpdateEnvironment implements UpdateEnvironment {
  /**
   * The context project
   */
  private final Project project;
  /**
   * The settings for VCS
   */
  private final GitVcsSettings settings;

  private final Configurable config;

  public GitUpdateEnvironment(@NotNull Project project, @NotNull GitVcsSettings settings, @NotNull Configurable config) {
    this.project = project;
    this.settings = settings;
    this.config = config;
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
    //unused
  }

  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] filePaths,
                                         UpdatedFiles updatedFiles,
                                         ProgressIndicator progressIndicator,
                                         @NotNull Ref<SequentialUpdatesContext> sequentialUpdatesContextRef)
      throws ProcessCanceledException {
    return new GitUpdateSession(null);
  }

  public boolean validateOptions(Collection<FilePath> filePaths) {
    // TODO review it later
    return true;
  }

  @Nullable
  public Configurable createConfigurable(Collection<FilePath> files) {
    return null;
  }
}
