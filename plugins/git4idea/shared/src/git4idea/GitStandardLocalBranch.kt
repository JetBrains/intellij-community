// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
open class GitStandardLocalBranch(name: String) : GitBranch(name) {
  override val isRemote: Boolean = false

  override val fullName: @NlsSafe String
    get() = REFS_HEADS_PREFIX + name

  override fun compareTo(o: GitReference?): Int {
    if (o is GitStandardLocalBranch) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(name, o.name)
    }
    return super.compareTo(o)
  }
}
