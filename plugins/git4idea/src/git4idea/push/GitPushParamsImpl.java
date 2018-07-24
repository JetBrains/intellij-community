// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class GitPushParamsImpl implements GitPushParams {
  @NotNull private final GitRemote myRemote;
  @NotNull private final String mySpec;
  private final boolean myForce;
  private final boolean mySetupTracking;
  private final boolean mySkipHooks;
  @Nullable private final String myTagMode;
  @NotNull private final List<ForceWithLease> myForceWithLease;

  /**
   * @deprecated Use {@link #GitPushParamsImpl(GitRemote, String, boolean, boolean, boolean, String, List)}
   */
  @Deprecated
  public GitPushParamsImpl(@NotNull GitRemote remote,
                           @NotNull String spec,
                           boolean force,
                           boolean setupTracking,
                           boolean skipHooks,
                           @Nullable String tagMode) {
    this(remote, spec, force, setupTracking, skipHooks, tagMode, Collections.emptyList());
  }

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

  @NotNull
  @Override
  public List<ForceWithLease> getForceWithLease() {
    return myForceWithLease;
  }


  public static class ForceWithLeaseAll implements ForceWithLease {
    @Nullable
    @Override
    public String getParameter() {
      return null;
    }
  }

  public static class ForceWithLeaseReference implements ForceWithLease {
    @NotNull private final String myReference;
    @Nullable private final String myCommit;

    public ForceWithLeaseReference(@NotNull String reference, @Nullable String commit) {
      myReference = reference;
      myCommit = commit;
    }

    @Nullable
    @Override
    public String getParameter() {
      if (myCommit != null) {
        return myReference + ":" + myCommit;
      }
      else {
        return myReference;
      }
    }
  }
}
