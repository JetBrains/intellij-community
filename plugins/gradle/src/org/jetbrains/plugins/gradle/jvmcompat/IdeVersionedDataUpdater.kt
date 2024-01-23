// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance(IdeVersionedDataUpdater::class.java)

@ApiStatus.Internal
abstract class IdeVersionedDataUpdater<T : IdeVersionedDataState>(
  private val dataStorage: IdeVersionedDataStorage<T>
) {
  abstract val configUrl: String
  abstract val updateInterval: Int

  open fun checkForUpdates() {
    if (updateInterval == 0 || configUrl.isEmpty()) {
      return
    }

    val state = dataStorage.state
    val lastUpdateTime = state?.lastUpdateTime ?: 0
    if (lastUpdateTime + TimeUnit.DAYS.toMillis(updateInterval.toLong()) <= System.currentTimeMillis()) {
      LOG.info("Updating version compatibility for ${this::class.java.name}." +
               " Last update was: ${lastUpdateTime}. Update interval: ${updateInterval} Url to update $configUrl")
      retrieveNewData(configUrl)
    }
    else {
      LOG.debug("Will not update version compatibility for ${this::class.java.name}. Last update was: ${lastUpdateTime}. Update interval: ${updateInterval} Url to update $configUrl")
    }
  }

  private fun retrieveNewData(configUrl: String) {
    try {
      val json = HttpRequests.request(configUrl)
        .forceHttps(!ApplicationManager.getApplication().isUnitTestMode)
        .productNameAsUserAgent()
        .readString()
      dataStorage.setStateAsString(json)
      LOG.info("IDE versioned data for ${this::class.java.name} was updated")
    }
    catch (e: Exception) {
      LOG.warn("Could not download new IDE versioned data for ${this::class.java.name}", e)
    }
  }
}