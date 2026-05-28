// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface GitPushParams {
  @NotNull
  GitRemote getRemote();

  @NotNull
  String getSpec();

  boolean isForce();

  boolean shouldSetupTracking();

  boolean shouldSkipHooks();

  @Nullable
  String getTagMode();

  @NotNull
  List<ForceWithLease> getForceWithLease();


  interface ForceWithLease {
    @Nullable
    String getParameter();
  }
}

