// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history


class GitCommitRequirements(val includeRootChanges: Boolean = true,
                            val diffRenameLimit: DiffRenameLimit = DiffRenameLimit.GIT_CONFIG,
                            val diffToParentsInMerges: Boolean = false,
                            val preserveOrder: Boolean = true) {
  enum class DiffRenameLimit {
    /**
     * Use zero value
     */
    INFINITY,
    /**
     * Use value set in registry (usually 1000)
     */
    REGISTRY,
    /**
     * Use value set in users git.config
     */
    GIT_CONFIG,
    /**
     * Disable renames detection
     */
    NO_RENAMES
  }

  companion object {
    @JvmField
    val DEFAULT = GitCommitRequirements()
  }
}
