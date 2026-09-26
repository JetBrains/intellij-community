// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action.mq;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;

public abstract class HgMqAppliedPatchAction extends HgMqLogAction {

  @Override
  protected boolean isEnabled(@NotNull HgRepository repository, @NotNull Hash commit) {
    return super.isEnabled(repository, commit) && isAppliedPatch(repository, commit);
  }

  public static boolean isAppliedPatch(@NotNull HgRepository repository, final @NotNull Hash hash) {
    return ContainerUtil.exists(repository.getMQAppliedPatches(), info -> info.getHash().equals(hash));
  }
}
