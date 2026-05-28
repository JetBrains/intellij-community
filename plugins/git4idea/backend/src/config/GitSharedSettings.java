// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static git4idea.log.GitRefManager.MAIN;
import static git4idea.log.GitRefManager.MASTER;

@State(name = "GitSharedSettings", storages = @Storage("vcs.xml"))
public class GitSharedSettings implements PersistentStateComponent<GitSharedSettings.State> {
  private static final Logger LOG = Logger.getInstance(GitSharedSettings.class);
  private final Project myProject;

  public GitSharedSettings(@NotNull Project project){
    myProject = project;
  }

  public static class State {
    public List<@NonNls String> FORCE_PUSH_PROHIBITED_PATTERNS = List.of(MASTER, MAIN);
    public boolean synchronizeBranchProtectionRules = true;
  }

  private State myState = new State();

  @Override
  public @Nullable State getState() {
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

  public @NotNull List<String> getForcePushProhibitedPatterns() {
    return Collections.unmodifiableList(myState.FORCE_PUSH_PROHIBITED_PATTERNS);
  }

  public void setForcePushProhibitedPatterns(@NotNull List<String> patterns) {
    myState.FORCE_PUSH_PROHIBITED_PATTERNS = new ArrayList<>(patterns);
  }

  public boolean isBranchProtected(@NotNull String branch) {
    // let "master" match only "master" and not "any-master-here" by default
    return Stream.of(getForcePushProhibitedPatterns(), getAdditionalProhibitedPatterns())
      .flatMap(Collection::stream)
      .anyMatch(pattern -> isMatchSafe(branch, pattern));
  }

  private static boolean isMatchSafe(@NotNull String branch, @NotNull String pattern) {
    try {
      return branch.matches("^" + pattern + "$");
    }
    catch (PatternSyntaxException e) {
      if (LOG.isDebugEnabled()) {
        String cause = StringUtil.substringBefore(e.getMessage(), "\n");
        LOG.debug(GitBundle.message("settings.protected.branched.validation", pattern, cause));
      }
    }

    return false;
  }

  public @NotNull List<String> getAdditionalProhibitedPatterns() {
    if (!isSynchronizeBranchProtectionRules()) return Collections.emptyList();

    return GitProtectedBranchProvider.getProtectedBranchPatterns(myProject);
  }
}
