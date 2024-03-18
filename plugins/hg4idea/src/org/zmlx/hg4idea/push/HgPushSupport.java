// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Objects;

public class HgPushSupport extends PushSupport<HgRepository, HgPushSource, HgTarget> {

  private final @NotNull Project myProject;
  private final @NotNull HgVcs myVcs;
  private final @NotNull HgProjectSettings mySettings;
  private final @NotNull PushSettings myCommonPushSettings;

  public HgPushSupport(@NotNull Project project) {
    myProject = project;
    myVcs = Objects.requireNonNull(HgVcs.getInstance(myProject));
    mySettings = myVcs.getProjectSettings();
    myCommonPushSettings = project.getService(PushSettings.class);
  }

  @Override
  public @NotNull AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public @NotNull Pusher<HgRepository, HgPushSource, HgTarget> getPusher() {
    return new HgPusher();
  }

  @Override
  public @NotNull OutgoingCommitsProvider<HgRepository, HgPushSource, HgTarget> getOutgoingCommitsProvider() {
    return new HgOutgoingCommitsProvider();
  }

  @Override
  public @Nullable HgTarget getDefaultTarget(@NotNull HgRepository repository) {
    String defaultPushPath = repository.getRepositoryConfig().getDefaultPushPath();
    return defaultPushPath == null ? null : new HgTarget(defaultPushPath, Objects.requireNonNull(repository.getCurrentBranchName()));
  }

  @Override
  public @Nullable HgTarget getDefaultTarget(@NotNull HgRepository repository, @NotNull HgPushSource source) {
    return getDefaultTarget(repository);
  }

  @Override
  public HgPushSource getSource(@NotNull HgRepository repository) {
    String localBranch = repository.getCurrentBranchName();
    if (localBranch == null) return null;
    return new HgPushSource(localBranch);
  }

  @Override
  public @NotNull RepositoryManager<HgRepository> getRepositoryManager() {
    return HgUtil.getRepositoryManager(myProject);
  }

  @Override
  public @Nullable VcsPushOptionsPanel createOptionsPanel() {
    return new HgPushOptionsPanel();
  }

  @Override
  public @NotNull PushTargetPanel<HgTarget> createTargetPanel(@NotNull HgRepository repository,
                                                              @NotNull HgPushSource source,
                                                              @Nullable HgTarget defaultTarget) {
    return new HgPushTargetPanel(repository, source, defaultTarget);
  }

  @Override
  public boolean isForcePushAllowed(@NotNull HgRepository repo, @NotNull HgTarget target) {
    return true;
  }

  @Override
  public boolean shouldRequestIncomingChangesForNotCheckedRepositories() {
    // load commit for all repositories if sync
    return mySettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC;
  }

  @Override
  public void saveSilentForcePushTarget(@NotNull HgTarget target) {
    myCommonPushSettings.addForcePushTarget(target.getPresentation(), target.getBranchName());
  }

  @Override
  public boolean isSilentForcePushAllowed(@NotNull HgTarget target) {
    return myCommonPushSettings.containsForcePushTarget(target.getPresentation(), target.getBranchName());
  }
}
