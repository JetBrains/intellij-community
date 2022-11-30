// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import git4idea.log.GitRefManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@State(name = "GitSharedSettings", storages = @Storage("vcs.xml"))
public class GitSharedSettings implements PersistentStateComponent<GitSharedSettings.State> {

  private final Project myProject;

  public GitSharedSettings(@NotNull Project project){
    myProject = project;
  }

  public static class State {
    public List<@NonNls String> FORCE_PUSH_PROHIBITED_PATTERNS = List.of(GitRefManager.MASTER);
    public boolean synchronizeBranchProtectionRules = true;
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
    return project.getService(GitSharedSettings.class);
  }

  public boolean isSynchronizeBranchProtectionRules() {
    return myState.synchronizeBranchProtectionRules;
  }

  public void setSynchronizeBranchProtectionRules(boolean sync) {
    myState.synchronizeBranchProtectionRules = sync;
  }

  @NotNull
  public List<String> getForcePushProhibitedPatterns() {
    return Collections.unmodifiableList(myState.FORCE_PUSH_PROHIBITED_PATTERNS);
  }

  public void setForcePushProhibitedPatterns(@NotNull List<String> patterns) {
    myState.FORCE_PUSH_PROHIBITED_PATTERNS = new ArrayList<>(patterns);
  }

  public boolean isBranchProtected(@NotNull String branch) {
    // let "master" match only "master" and not "any-master-here" by default
    return Stream.of(getForcePushProhibitedPatterns(), getAdditionalProhibitedPatterns())
      .flatMap(Collection::stream)
      .anyMatch(pattern -> branch.matches("^" + pattern + "$"));
  }

  @NotNull
  public List<String> getAdditionalProhibitedPatterns() {
    if (!isSynchronizeBranchProtectionRules()) return Collections.emptyList();

    return GitProtectedBranchProvider.getProtectedBranchPatterns(myProject);
  }
}
