// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.containers.HashingStrategy
import com.intellij.vcs.git.CaseSensitivityInfoHolder
import org.jetbrains.annotations.ApiStatus

/**
 * The base class for named git references, like branches and tags.
 */
@ApiStatus.NonExtendable
abstract class GitReference(
  /**
   * The name of the reference, e.g. "origin/master" or "feature".
   * @see [fullName]
   */
  val name: @NlsSafe String,
) : Comparable<GitReference?> {
  /**
   * The full name of the reference, e.g. "refs/remotes/origin/master" or "refs/heads/master".
   */
  abstract val fullName: @NlsSafe String

  override fun toString(): String {
    return fullName
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    val reference = o as GitReference
    return BRANCH_NAME_HASHING_STRATEGY.equals(name, reference.name)
  }

  override fun hashCode(): Int {
    return BRANCH_NAME_HASHING_STRATEGY.hashCode(name)
  }

  override fun compareTo(other: GitReference?): Int {
    // NB: update overridden comparators on modifications
    return if (other == null) 1 else REFS_NAMES_COMPARATOR.compare(fullName, other.fullName)
  }

  companion object {
    @JvmField
    val BRANCH_NAME_HASHING_STRATEGY: HashingStrategy<String?> =
      if (CaseSensitivityInfoHolder.caseSensitive) HashingStrategy.canonical() else HashingStrategy.caseInsensitive()

    @JvmField
    val REFS_NAMES_COMPARATOR: Comparator<String> = NaturalComparator.INSTANCE
  }
}
