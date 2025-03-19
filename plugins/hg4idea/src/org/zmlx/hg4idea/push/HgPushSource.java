// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.PushSource;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public class HgPushSource implements PushSource {
  private final @NotNull @NlsSafe String myBranch;

  public HgPushSource(@NotNull @NlsSafe String branch) {
    myBranch = branch;
  }

  @Override
  public @NotNull String getPresentation() {
    return myBranch;
  }

  public @NlsSafe @NotNull String getBranch() {
    return myBranch;   // presentation may differ from branch
  }
}
