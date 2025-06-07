// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class GitRepositoryIdCache(private val project: Project, cs: CoroutineScope) {
  private val cache: MutableMap<RepositoryId, GitRepository?> = ConcurrentHashMap()

  init {
    project.messageBus.connect(cs).subscribe<VcsRepositoryMappingListener>(
      VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
      VcsRepositoryMappingListener { cache.clear() },
    )
  }

  fun get(repositoryId: RepositoryId): GitRepository? = cache.compute(repositoryId) { _, value ->
    value ?: GitRepositoryManager.getInstance(project).repositories.find { it.rpcId == repositoryId }
  }

  companion object {
    fun getInstance(project: Project) = project.service<GitRepositoryIdCache>()
  }
}