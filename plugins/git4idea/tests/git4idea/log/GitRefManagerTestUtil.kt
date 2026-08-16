// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsRefImpl
import git4idea.branch.GitBranchUtil
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.readAllRefs
import org.assertj.core.api.Assertions.assertThat

/**
 * Creates the given refs in the repository at the current HEAD and returns them.
 *
 * A ref name is interpreted as `HEAD`, a remote branch (`origin/...`), a tag (`refs/tags/...`),
 * or a local branch otherwise. Local branches which have a matching remote branch among [refs] become tracking.
 */
internal fun GitSingleRepoContext.given(vararg refs: String): Collection<VcsRef> {
  val result = mutableListOf<VcsRef>()
  cd(projectRoot)
  val hash = HashImpl.build(git("rev-parse HEAD"))
  for (refName in refs) {
    when {
      isHead(refName) -> result.add(ref(hash, "HEAD", GitRefManager.HEAD))
      isRemoteBranch(refName) -> {
        git("update-ref refs/remotes/$refName ${hash.asString()}")
        result.add(ref(hash, refName, GitRefManager.REMOTE_BRANCH))
      }
      isTag(refName) -> {
        git("update-ref $refName ${hash.asString()}")
        result.add(ref(hash, GitBranchUtil.stripRefsPrefix(refName), GitRefManager.TAG))
      }
      else -> {
        git("update-ref refs/heads/$refName ${hash.asString()}")
        result.add(ref(hash, refName, GitRefManager.LOCAL_BRANCH))
      }
    }
  }
  setUpTracking(result)
  repo.update()
  return result
}

/**
 * Reads the refs actually present in the repository and returns them in the order of the given [refNames].
 */
internal fun GitSingleRepoContext.expect(vararg refNames: String): List<VcsRef> {
  val refs = readAllRefs(projectRoot, project.getService(VcsLogObjectsFactory::class.java))
  return refNames.map { refName ->
    val item = refs.find { it.name == GitBranchUtil.stripRefsPrefix(refName) }
    assertThat(item).describedAs("Ref $refName not found among $refs").isNotNull()
    item!!
  }
}

private fun isHead(name: String) = name == "HEAD"

private fun isTag(name: String) = name.startsWith("refs/tags/")

private fun isRemoteBranch(name: String) = name.startsWith("origin/")

private fun GitSingleRepoContext.ref(hash: Hash, name: String, type: VcsRefType): VcsRef = VcsRefImpl(hash, name, type, projectRoot)

private fun GitSingleRepoContext.setUpTracking(refs: Collection<VcsRef>) {
  cd(projectRoot)
  for (ref in refs) {
    if (ref.type != GitRefManager.LOCAL_BRANCH) continue
    val localBranch = ref.name
    val hasMatchingRemote = refs.any {
      it.type == GitRefManager.REMOTE_BRANCH && it.name.replace("origin/", "") == localBranch
    }
    if (hasMatchingRemote) {
      git("config branch.$localBranch.remote origin")
      git("config branch.$localBranch.merge refs/heads/$localBranch")
    }
  }
}
