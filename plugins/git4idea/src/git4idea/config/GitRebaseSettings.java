// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;
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
  public void loadState(@NotNull State state) {
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

  @Nullable
  public String getOnto() {
    return myState.ONTO;
  }

  public void setOnto(@Nullable String onto) {
    myState.ONTO = onto;
  }

}
