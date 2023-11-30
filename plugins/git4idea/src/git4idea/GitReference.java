// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * The base class for named git references, like branches and tags.
 */
public abstract class GitReference implements Comparable<GitReference> {

  public static final HashingStrategy<String> BRANCH_NAME_HASHING_STRATEGY =
    SystemInfoRt.isFileSystemCaseSensitive ? HashingStrategy.canonical() : HashingStrategy.caseInsensitive();

  protected final @NotNull String myName;

  public GitReference(@NotNull String name) {
    myName = name;
  }

  /**
   * @return the name of the reference, e.g. "origin/master" or "feature".
   * @see #getFullName()
   */
  public @NlsSafe @NotNull String getName() {
    return myName;
  }

  /**
   * @return the full name of the reference, e.g. "refs/remotes/origin/master" or "refs/heads/master".
   */
  public abstract @NlsSafe @NotNull String getFullName();

  @Override
  public String toString() {
    return getFullName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitReference reference = (GitReference)o;
    return BRANCH_NAME_HASHING_STRATEGY.equals(myName, reference.myName);
  }

  @Override
  public int hashCode() {
    return BRANCH_NAME_HASHING_STRATEGY.hashCode(myName);
  }

  @Override
  public int compareTo(GitReference o) {
    // NB: update overridden comparators on modifications
    return o == null ? 1 : StringUtil.compare(getFullName(), o.getFullName(), SystemInfo.isFileSystemCaseSensitive);
  }
}
