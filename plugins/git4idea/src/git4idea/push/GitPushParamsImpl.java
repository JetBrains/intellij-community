// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitPushParamsImpl implements GitPushParams {
  @NotNull private final GitRemote myRemote;
  @NotNull private final String mySpec;
  private final boolean myForce;
  private final boolean mySetupTracking;
  private final boolean mySkipHooks;
  @Nullable private final String myTagMode;

  public GitPushParamsImpl(@NotNull GitRemote remote,
                           @NotNull String spec,
                           boolean force,
                           boolean setupTracking,
                           boolean skipHooks,
                           @Nullable String tagMode) {

    myRemote = remote;
    mySpec = spec;
    myForce = force;
    mySetupTracking = setupTracking;
    mySkipHooks = skipHooks;
    myTagMode = tagMode;
  }

  @NotNull
  @Override
  public GitRemote getRemote() {
    return myRemote;
  }

  @NotNull
  @Override
  public String getSpec() {
    return mySpec;
  }

  @Override
  public boolean isForce() {
    return myForce;
  }

  @Override
  public boolean shouldSetupTracking() {
    return mySetupTracking;
  }

  @Override
  public boolean shouldSkipHooks() {
    return mySkipHooks;
  }

  @Nullable
  @Override
  public String getTagMode() {
    return myTagMode;
  }
}
