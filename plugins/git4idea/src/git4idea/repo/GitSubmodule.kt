// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil

private val LOG = logger<GitSubmodule>()

class GitSubmodule(
  val repository: GitRepository,
  val parent: GitRepository)

fun GitRepository.asSubmodule() : GitSubmodule? {
  val repositoryManager = GitRepositoryManager.getInstance(project)
  val parent = repositoryManager.repositories.find { it.isParentRepositoryFor(this) }
  return if (parent != null) GitSubmodule(this, parent) else null
}

private fun GitRepository.isParentRepositoryFor(submodule: GitRepository): Boolean {
  return VfsUtil.isAncestor(root, submodule.root, true) &&
         submodules.isNotEmpty() &&
         runReadAction { submodules.any { module -> root.findFileByRelativePath(module.path) == submodule.root } }
}

fun GitRepository.isSubmodule(): Boolean = asSubmodule() != null

fun GitRepository.getDirectSubmodules(): Collection<GitRepository> {
  return submodules.mapNotNull { module ->
    val submoduleDir = root.findFileByRelativePath(module.path)
    if (submoduleDir == null) {
      LOG.debug("submodule dir not found at declared path [${module.path}] of root [$root]")
      return@mapNotNull null
    }
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(submoduleDir)
    if (repository == null) {
      LOG.warn("Submodule not registered as a repository: $submoduleDir")
    }
    return@mapNotNull repository
  }
}
