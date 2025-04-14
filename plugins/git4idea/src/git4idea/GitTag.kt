// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import git4idea.branch.GitBranchUtil
import org.jetbrains.annotations.NonNls

class GitTag(name: String) : GitReference(GitBranchUtil.stripRefsPrefix(name)) {
  override val fullName: String
    get() = REFS_TAGS_PREFIX + name

  override fun compareTo(o: GitReference?): Int {
    if (o is GitTag) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(name, o.name)
    }
    return super.compareTo(o)
  }

  companion object {
    const val REFS_TAGS_PREFIX: @NonNls String = "refs/tags/"
  }
}
