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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.Nullable;

@State(name = "Git.Rebase.Settings", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class GitRebaseSettings implements PersistentStateComponent<GitRebaseSettings.State> {

  private State myState = new State();

  public static class State {
    public boolean INTERACTIVE = true;
    public boolean PRESERVE_MERGES = false;
    public boolean SHOW_TAGS = false;
    public boolean SHOW_REMOTE_BRANCHES = false;
    public String ONTO = null;
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public boolean isInteractive() {
    return myState.INTERACTIVE;
  }

  public void setInteractive(boolean interactive) {
    myState.INTERACTIVE = interactive;
  }

  public boolean isPreserveMerges() {
    return myState.PRESERVE_MERGES;
  }

  public void setPreserveMerges(boolean preserveMerges) {
    myState.PRESERVE_MERGES = preserveMerges;
  }

  public boolean showTags() {
    return myState.SHOW_TAGS;
  }

  public void setShowTags(boolean showTags) {
    myState.SHOW_TAGS = showTags;
  }

  public boolean showRemoteBranches() {
    return myState.SHOW_REMOTE_BRANCHES;
  }

  public void setShowRemoteBranches(boolean showRemoteBranches) {
    myState.SHOW_REMOTE_BRANCHES = showRemoteBranches;
  }

  @Nullable
  public String getOnto() {
    return myState.ONTO;
  }

  public void setOnto(@Nullable String onto) {
    myState.ONTO = onto;
  }

}
