// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

public class HgPushSupport extends PushSupport<HgRepository, HgPushSource, HgTarget> {

  @NotNull private final Project myProject;
  @NotNull private final HgVcs myVcs;
  @NotNull private final HgProjectSettings mySettings;
  @NotNull private final PushSettings myCommonPushSettings;

  public HgPushSupport(@NotNull Project project) {
    myProject = project;
    myVcs = ObjectUtils.assertNotNull(HgVcs.getInstance(myProject));
    mySettings = myVcs.getProjectSettings();
    myCommonPushSettings = ServiceManager.getService(project, PushSettings.class);
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @NotNull
  @Override
  public Pusher<HgRepository, HgPushSource, HgTarget> getPusher() {
    return new HgPusher();
  }

  @NotNull
  @Override
  public OutgoingCommitsProvider<HgRepository, HgPushSource, HgTarget> getOutgoingCommitsProvider() {
    return new HgOutgoingCommitsProvider();
  }

  @Nullable
  @Override
  public HgTarget getDefaultTarget(@NotNull HgRepository repository) {
    String defaultPushPath = repository.getRepositoryConfig().getDefaultPushPath();
    return defaultPushPath == null ? null : new HgTarget(defaultPushPath, repository.getCurrentBranchName());
  }

  @NotNull
  @Override
  public HgPushSource getSource(@NotNull HgRepository repository) {
    String localBranch = repository.getCurrentBranchName();
    return new HgPushSource(localBranch);
  }

  @NotNull
  @Override
  public RepositoryManager<HgRepository> getRepositoryManager() {
    return HgUtil.getRepositoryManager(myProject);
  }

  @Override
  @Nullable
  public VcsPushOptionsPanel createOptionsPanel() {
    return new HgPushOptionsPanel();
  }

  @Override
  @NotNull
  public PushTargetPanel<HgTarget> createTargetPanel(@NotNull HgRepository repository, @Nullable HgTarget defaultTarget) {
    return new HgPushTargetPanel(repository, defaultTarget);
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
