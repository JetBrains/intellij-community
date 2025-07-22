// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import fleet.multiplatform.shims.ConcurrentHashMap
import git4idea.config.GitConfigUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.jvm.optionals.getOrNull

@ApiStatus.Experimental
@Service(Service.Level.APP)
class GitConfigurationCache() : GitConfigurationCacheBase() {
  companion object {
    @JvmStatic
    fun getInstance(): GitConfigurationCache = service()
  }
}

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class GitProjectConfigurationCache(val project: Project) : GitConfigurationCacheBase() {
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
  fun readRepositoryConfig(root: VirtualFile, key: String): String? {
    return computeCachedValue(RepositoryConfigKey(root, key)) {
      try {
        GitConfigUtil.getValue(project, root, key)
      }
      catch (e: VcsException) {
        logger<GitProjectConfigurationCache>().warn(e)
        null
      }
    }
  }

  fun clearForRepo(repository: GitRepository) {
    cache.keys.removeIf {
      it is GitRepositoryConfigKey && it.root == repository.root
    }
  }

  private fun clearInvalidKeys() {
    val gitRepositoryManager = GitRepositoryManager.getInstance(project)
    val activeRoots = gitRepositoryManager.repositories.mapTo(mutableSetOf()) { it.root }

    cache.keys.removeIf {
      it is GitRepositoryConfigKey && it.root !in activeRoots
    }
  }

  data class RepositoryConfigKey(override val root: VirtualFile, val key: String) : GitRepositoryConfigKey<String?>
}

abstract class GitConfigurationCacheBase() : Disposable {
  protected val cache: MutableMap<GitConfigKey<*>, Optional<*>> = ConcurrentHashMap()

  @RequiresBackgroundThread
  @Suppress("UNCHECKED_CAST")
  fun <T> computeCachedValue(configKey: GitConfigKey<T>, computeValue: () -> T): T {
    return cache.computeIfAbsent(configKey) { k -> Optional.ofNullable(computeValue()) }.getOrNull() as T
  }

  fun clearCache() {
    cache.clear()
  }

  override fun dispose() {
    clearCache()
  }
}

interface GitConfigKey<T>
interface GitRepositoryConfigKey<T> : GitConfigKey<T> {
  val root: VirtualFile
}
