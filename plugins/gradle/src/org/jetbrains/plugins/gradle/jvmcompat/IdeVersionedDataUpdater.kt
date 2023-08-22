// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
abstract class IdeVersionedDataUpdater<T: IdeVersionedDataState>(
  private val dataStorage: IdeVersionedDataStorage<T>
) {
  companion object {
    private val LOG = Logger.getInstance(IdeVersionedDataUpdater::class.java)
  }

  abstract val configUrl: String
  abstract val updateInterval: Int

  open fun checkForUpdates(): Future<*> {
    return if (updateInterval == 0 || StringUtil.isEmpty(configUrl)) {
      CompletableFuture.completedFuture<Any?>(null)
    }
    else ApplicationManager.getApplication().executeOnPooledThread {
      val state = dataStorage.state
      val lastUpdateTime = state?.lastUpdateTime ?: 0
      if (lastUpdateTime + TimeUnit.DAYS.toMillis(updateInterval.toLong()) <= System.currentTimeMillis()) {
        retrieveNewData(configUrl)
      }
    }
  }

  private fun retrieveNewData(configUrl: String) {
    try {
      val json = HttpRequests.request(configUrl)
        .forceHttps(!ApplicationManager.getApplication().isUnitTestMode)
        .productNameAsUserAgent()
        .readString()
      dataStorage.setStateAsString(json)
    }
    catch (e: Exception) {
      LOG.warn("Could not download new IDE versioned data for ${this::class.java.name}", e)
    }
  }
}