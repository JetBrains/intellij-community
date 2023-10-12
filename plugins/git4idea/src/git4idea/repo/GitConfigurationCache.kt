// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import git4idea.config.GitExecutableListener
import git4idea.config.GitExecutableManager
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@ApiStatus.Experimental
@Service(Service.Level.APP)
class GitConfigurationCache : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(): GitConfigurationCache = service()
  }

  private val cache: MutableMap<ConfigKey<*>, CompletableFuture<*>> = ConcurrentCollectionFactory.createConcurrentMap()

  init {
    val connection: MessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(this)
    connection.subscribe<GitExecutableListener>(GitExecutableManager.TOPIC, GitExecutableListener { clearCache() })
  }

  @RequiresBackgroundThread
  fun <T> computeCachedValue(configKey: ConfigKey<T>, computeValue: () -> T): T {
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

  interface ConfigKey<T>
}
