// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(name = "GitSharedSettings", storages = @Storage("vcs.xml"))
public class GitSharedSettings implements PersistentStateComponent<GitSharedSettings.State> {

  public static class State {
    public List<String> FORCE_PUSH_PROHIBITED_PATTERNS = ContainerUtil.newArrayList("master");
  }

  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static GitSharedSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitSharedSettings.class);
  }

  @NotNull
  public List<String> getForcePushProhibitedPatterns() {
    return Collections.unmodifiableList(myState.FORCE_PUSH_PROHIBITED_PATTERNS);
  }

  public void setForcePushProhibitedPatters(@NotNull List<String> patterns) {
    myState.FORCE_PUSH_PROHIBITED_PATTERNS = new ArrayList<>(patterns);
  }

  public boolean isBranchProtected(@NotNull String branch) {
    // let "master" match only "master" and not "any-master-here" by default
    return getForcePushProhibitedPatterns().stream().anyMatch(pattern -> branch.matches("^" + pattern + "$"));
  }
}
