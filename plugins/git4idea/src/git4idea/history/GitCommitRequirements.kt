// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.util.registry.Registry

class GitCommitRequirements(val includeRootChanges: Boolean = true,
                            val diffRenameLimit: DiffRenameLimit = DiffRenameLimit.GIT_CONFIG,
                            val diffToParentsInMerges: Boolean = false,
                            val preserveOrder: Boolean = true) {

  fun configParameters(): List<String> {
    val result = mutableListOf<String>()
    when (diffRenameLimit) {
      DiffRenameLimit.INFINITY -> result.add(renameLimit(0))
      DiffRenameLimit.REGISTRY -> result.add(renameLimit(Registry.intValue("git.diff.renameLimit")))
      DiffRenameLimit.NO_RENAMES -> result.add("diff.renames=false")
      else -> {
      }
    }

    if (!includeRootChanges) {
      result.add("log.showRoot=false")
    }
    return result
  }

  fun commandParameters(): List<String> {
    val result = mutableListOf<String>()
    if (diffRenameLimit != DiffRenameLimit.NO_RENAMES) {
      result.add("-M")
    }
    if (diffToParentsInMerges) {
      result.add("-m")
    }
    return result
  }

  private fun renameLimit(limit: Int): String {
    return "diff.renameLimit=$limit"
  }

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
