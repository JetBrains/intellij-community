// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import git4idea.branch.GitBranchUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class GitTag extends GitReference {
  public static final @NonNls String REFS_TAGS_PREFIX = "refs/tags/";

  public GitTag(@NotNull String name) {
    super(GitBranchUtil.stripRefsPrefix(name));
  }

  @Override
  public @NotNull String getFullName() {
    return REFS_TAGS_PREFIX + myName;
  }

  @Override
  public int compareTo(GitReference o) {
    if (o instanceof GitTag) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(myName, o.myName);
    }
    return super.compareTo(o);
  }
}
