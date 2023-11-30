// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import git4idea.config.UpdateMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

// holds settings chosen in GitRejectedPushUpdate dialog to reuse if the next push is rejected again.
public class PushUpdateSettings {
  private final @NotNull UpdateMethod myUpdateMethod;

  PushUpdateSettings(@NotNull UpdateMethod updateMethod) {
    myUpdateMethod = updateMethod;
  }

  @NotNull
  UpdateMethod getUpdateMethod() {
    return myUpdateMethod;
  }

  @Override
  public @NonNls String toString() {
    return String.format("UpdateSettings{myUpdateMethod=%s}", myUpdateMethod);
  }
}
