// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class GitTag extends GitReference {
  public static final @NonNls String REFS_TAGS_PREFIX = "refs/tags/";

  public GitTag(@NotNull String name) {
    super(name);
  }

  @Override
  @NotNull
  public String getFullName() {
    return REFS_TAGS_PREFIX + myName;
  }
}
