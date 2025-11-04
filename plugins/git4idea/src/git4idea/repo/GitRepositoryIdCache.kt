// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class GitRepositoryIdCache(private val project: Project, cs: CoroutineScope) {
  private val cache: MutableMap<RepositoryId, GitRepository> = ConcurrentHashMap()

  init {
    project.messageBus.connect(cs).subscribe<VcsRepositoryMappingListener>(
      VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
      VcsRepositoryMappingListener { cache.clear() },
    )
  }

  fun get(repositoryId: RepositoryId): GitRepository? = cache.compute(repositoryId) { _, value ->
    value ?: GitRepositoryManager.getInstance(project).repositories.find { it.rpcId == repositoryId }
  }

  fun resolveAll(repositoryIds: List<RepositoryId>): List<GitRepository> {
    val resolved = repositoryIds.mapNotNull(::get)

    check(resolved.size == repositoryIds.size) {
      val resolvedIds = resolved.mapTo(mutableSetOf()) { it.rpcId }
      val notFound = repositoryIds.filterNot { it in resolvedIds }
      "Failed to resolve repositories: $notFound"
    }

    return resolved
  }

  companion object {
    fun getInstance(project: Project) = project.service<GitRepositoryIdCache>()
  }
}