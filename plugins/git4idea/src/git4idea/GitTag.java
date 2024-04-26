// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class GitTag extends GitReference {
  public static final @NonNls String REFS_TAGS_PREFIX = "refs/tags/";

  public GitTag(@NotNull String name) {
    super(name);
  }

  @Override
  public @NotNull String getFullName() {
    return REFS_TAGS_PREFIX + myName;
  }

  @Override
  public int compareTo(GitReference o) {
    if (o instanceof GitTag) {
      // optimization: do not build getFullName
      return StringUtil.compare(myName, o.myName, SystemInfo.isFileSystemCaseSensitive);
    }
    return super.compareTo(o);
  }
}
