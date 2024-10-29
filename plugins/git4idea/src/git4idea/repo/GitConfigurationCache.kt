// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import git4idea.config.GitConfigUtil
import git4idea.config.GitExecutableListener
import git4idea.config.GitExecutableManager
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@ApiStatus.Experimental
@Service(Service.Level.APP)
class GitConfigurationCache : GitConfigurationCacheBase() {
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
    connection.subscribe(GitConfigListener.TOPIC, object : GitConfigListener {
      override fun notifyConfigChanged(repository: GitRepository) {
        clearForRepo(repository)
      }
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

  private fun clearForRepo(repository: GitRepository) {
    cache.keys.removeIf {
      it is GitRepositoryConfigKey && it.repository == repository
    }
  }

  private fun clearInvalidKeys() {
    cache.keys.removeIf {
      it is GitRepositoryConfigKey && it.repository.isDisposed
    }
  }

  data class RepoConfigKey(override val repository: GitRepository, val key: String) : GitRepositoryConfigKey<String?>
}

abstract class GitConfigurationCacheBase : Disposable {
  protected val cache: MutableMap<GitConfigKey<*>, CompletableFuture<*>> = ConcurrentCollectionFactory.createConcurrentMap()

  init {
    val connection: MessageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe<GitExecutableListener>(GitExecutableManager.TOPIC, GitExecutableListener { clearCache() })
  }

  @RequiresBackgroundThread
  fun <T> computeCachedValue(configKey: GitConfigKey<T>, computeValue: () -> T): T {
    val future = CompletableFuture<T>()

    @Suppress("UNCHECKED_CAST")
    val oldFuture = cache.putIfAbsent(configKey, future) as CompletableFuture<T>?
    if (oldFuture != null) {
      try {
        return oldFuture.get()
      }
      catch (e: ExecutionException) {
        throw e.cause ?: e
      }
      catch (e: CancellationException) {
        if (oldFuture.isCancelled) {
          ProgressManager.checkCanceled()
          return computeValue() // another progress was cancelled, compute without cache
        }
        else {
          throw e
        }
      }
    }
    else {
      try {
        val result = computeValue()
        future.complete(result)
        return result
      }
      catch (e: ProcessCanceledException) {
        cache.remove(configKey, future)
        future.cancel(true)
        throw e
      }
      catch (e: Throwable) {
        future.completeExceptionally(e)
        throw e
      }
    }
  }

  fun clearCache() {
    cache.clear()
  }

  override fun dispose() {
  }
}

interface GitConfigKey<T>
interface GitRepositoryConfigKey<T> : GitConfigKey<T> {
  val repository: GitRepository
}
