// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GitPushParamsImpl implements GitPushParams {
  private final @NotNull GitRemote myRemote;
  private final @NotNull String mySpec;
  private final boolean myForce;
  private final boolean mySetupTracking;
  private final boolean mySkipHooks;
  private final @Nullable String myTagMode;
  private final @NotNull List<ForceWithLease> myForceWithLease;

  public GitPushParamsImpl(@NotNull GitRemote remote,
                           @NotNull String spec,
                           boolean force,
                           boolean setupTracking,
                           boolean skipHooks,
                           @Nullable String tagMode,
                           @NotNull List<ForceWithLease> forceWithLease) {
    myRemote = remote;
    mySpec = spec;
    myForce = force;
    mySetupTracking = setupTracking;
    mySkipHooks = skipHooks;
    myTagMode = tagMode;
    myForceWithLease = forceWithLease;
  }

  @Override
  public @NotNull GitRemote getRemote() {
    return myRemote;
  }

  @Override
  public @NotNull String getSpec() {
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

  @Override
  public @Nullable String getTagMode() {
    return myTagMode;
  }

  @Override
  public @NotNull List<ForceWithLease> getForceWithLease() {
    return myForceWithLease;
  }


  public static class ForceWithLeaseAll implements ForceWithLease {
    @Override
    public @Nullable String getParameter() {
      return null;
    }
  }

  public static class ForceWithLeaseReference implements ForceWithLease {
    private final @NotNull String myReference;
    private final @Nullable String myCommit;

    public ForceWithLeaseReference(@NotNull String reference, @Nullable String commit) {
      myReference = reference;
      myCommit = commit;
    }

    @Override
    public @Nullable String getParameter() {
      if (myCommit != null) {
        return myReference + ":" + myCommit;
      }
      else {
        return myReference;
      }
    }
  }
}
