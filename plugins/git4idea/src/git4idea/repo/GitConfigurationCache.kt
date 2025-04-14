// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.github.benmanes.caffeine.cache.AsyncCache
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import git4idea.config.GitConfigUtil
import git4idea.util.CaffeineUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service(Service.Level.APP)
class GitConfigurationCache(cs: CoroutineScope) : GitConfigurationCacheBase(cs) {
  companion object {
    @JvmStatic
    fun getInstance(): GitConfigurationCache = service()
  }
}

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class GitProjectConfigurationCache(val project: Project, cs: CoroutineScope) : GitConfigurationCacheBase(cs) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitProjectConfigurationCache = project.service()
  }

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe<VcsRepositoryMappingListener>(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
      clearInvalidKeys()
    })
  }

  @RequiresBackgroundThread
  fun readRepositoryConfig(repository: GitRepository, key: String): String? {
    return computeCachedValue(RepoConfigKey(repository, key)) {
      try {
        GitConfigUtil.getValue(repository.getProject(), repository.getRoot(), key)
      }
      catch (e: VcsException) {
        logger<GitProjectConfigurationCache>().warn(e)
        null
      }
    }
  }

  fun clearForRepo(repository: GitRepository) {
    cache.asMap().keys.removeIf {
      it is GitRepositoryConfigKey && it.repository == repository
    }
  }

  private fun clearInvalidKeys() {
    cache.asMap().keys.removeIf {
      it is GitRepositoryConfigKey && it.repository.isDisposed
    }
  }

  data class RepoConfigKey(override val repository: GitRepository, val key: String) : GitRepositoryConfigKey<String?>
}

abstract class GitConfigurationCacheBase(private val cs: CoroutineScope) : Disposable {
  protected val cache: AsyncCache<GitConfigKey<*>, Any?> = CaffeineUtil
    .withIoExecutor()
    .buildAsync()

  @RequiresBackgroundThread
  @Suppress("UNCHECKED_CAST")
  fun <T> computeCachedValue(configKey: GitConfigKey<T>, computeValue: suspend () -> T): T = cache.get(configKey) { _, _ ->
    cs.future { computeValue() }
  }.get() as T

  fun clearCache() {
    cache.synchronous().invalidateAll()
  }

  override fun dispose() {
    clearCache()
  }
}

interface GitConfigKey<T>
interface GitRepositoryConfigKey<T> : GitConfigKey<T> {
  val repository: GitRepository
}
