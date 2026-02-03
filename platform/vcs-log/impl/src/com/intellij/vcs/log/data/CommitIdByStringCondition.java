// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.vcs.log.CommitId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

@ApiStatus.Internal
public final class CommitIdByStringCondition implements Predicate<CommitId> {
  private final @NotNull String myHashString;

  public CommitIdByStringCondition(@NotNull String hashString) {
    myHashString = hashString;
  }

  @Override
  public boolean test(CommitId commitId) {
    return matches(commitId, myHashString);
  }

  public static boolean matches(@NotNull CommitId commitId, @NotNull String prefix) {
    return StringUtilRt.startsWithIgnoreCase(commitId.getHash().asString(), prefix);
  }
}
