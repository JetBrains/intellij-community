/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The application wide settings for the git
 */
@State(
  name = "Git.Application.Settings",
  storages = {
    @Storage(value = "git.xml", roamingType = RoamingType.PER_OS),
    @Storage(value = "vcs.xml", deprecated = true)
  }
)
public class GitVcsApplicationSettings implements PersistentStateComponent<GitVcsApplicationSettings.State> {
  private State myState = new State();

  /**
   * Kinds of SSH executable to be used with the git
   */
  public enum SshExecutable {
    IDEA_SSH,
    NATIVE_SSH,
  }

  public static class State {
    public String myPathToGit = null;
    public SshExecutable SSH_EXECUTABLE = null;
  }

  public static GitVcsApplicationSettings getInstance() {
    return ServiceManager.getService(GitVcsApplicationSettings.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  @NotNull
  public String getPathToGit() {
    if (myState.myPathToGit == null) {
      // detecting right away, this can be called from the default project without a call to GitVcs#activate()
      myState.myPathToGit = new GitExecutableDetector().detect();
    }
    return myState.myPathToGit;
  }

  public void setPathToGit(String pathToGit) {
    myState.myPathToGit = pathToGit;
  }

  public void setIdeaSsh(@NotNull SshExecutable executable) {
    myState.SSH_EXECUTABLE = executable;
  }

  @Nullable
  SshExecutable getIdeaSsh() {
    return myState.SSH_EXECUTABLE;
  }
}
