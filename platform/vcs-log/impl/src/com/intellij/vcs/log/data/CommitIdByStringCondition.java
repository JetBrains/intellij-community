// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.vcs.log.CommitId;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public final class CommitIdByStringCondition implements Predicate<CommitId> {
  @NotNull private final String myHashString;

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
