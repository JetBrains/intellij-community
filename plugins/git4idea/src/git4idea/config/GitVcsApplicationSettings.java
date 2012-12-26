/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/vcs.xml")})
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

  public void loadState(State state) {
    myState = state;
  }

  @NotNull
  public String getPathToGit() {
    if (myState.myPathToGit == null) {
      // can happen only if GitVcs#activate hasn't been called: it is when configurables are built, returning the default value.
      return GitExecutableDetector.DEFAULT_WIN_GIT;
    }
    return myState.myPathToGit;
  }

  /**
   * <p>This method differs from {@link #getPathToGit()} only in the @Nullable annotation: initially the path can be null,
   *    but after VCS is initialized for the first time, correct path is set in {@link git4idea.GitVcs} and is never null,
   *    so we want the @NotNull annotation there.</p>
   * <p>This method should be called only from {@link git4idea.GitVcs#activate()}.</p>
   *
   * @return Path to Git or null if the path was not written to the config yet.
   */
  @Nullable
  public String getPathToGitAtStartup() {
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
