// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * The base class for named git references, like branches and tags.
 */
public abstract class GitReference implements Comparable<GitReference> {

  public static final TObjectHashingStrategy<String> BRANCH_NAME_HASHING_STRATEGY = FilePathHashingStrategy.create();

  @NotNull protected final String myName;

  public GitReference(@NotNull String name) {
    myName = name;
  }

  /**
   * @return the name of the reference, e.g. "origin/master" or "feature".
   * @see #getFullName()
   */
  @NlsSafe
  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * @return the full name of the reference, e.g. "refs/remotes/origin/master" or "refs/heads/master".
   */
  @NlsSafe
  @NotNull
  public abstract String getFullName();

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
    return BRANCH_NAME_HASHING_STRATEGY.computeHashCode(myName);
  }

  @Override
  public int compareTo(GitReference o) {
    return o == null ? 1 : StringUtil.compare(getFullName(), o.getFullName(), SystemInfo.isFileSystemCaseSensitive);
  }
}
