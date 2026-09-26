// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.ide.actions.shouldUseFallbackSwitcher
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesCoroutineScopeProvider
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.util.coroutines.childScope
import fleet.rpc.client.durable
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val LOG by lazy { fileLogger() }

internal class RecentFileModelSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (shouldUseFallbackSwitcher()) return
    val synchronizationScope = RecentFilesCoroutineScopeProvider.getInstanceAsync(project).coroutineScope.childScope("RecentFilesModel frontend/backend synchronisation")

    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstanceAsync(project)
    synchronizationScope.launch {
      // A light session resolves FileSwitcherApi to the model of this process, so the subscriptions have to start
      // again once the session gains a backend and the backend takes the model over.
      synchronizeWithTheLocalModelOfALightSession(frontendRecentFilesModel, project)
      synchronizeWithTheModel(frontendRecentFilesModel, project)
    }
  }

  /**
   * Synchronizes with the local model of a light session, and returns once a backend connection appears, so that the
   * caller resolves [FileSwitcherApi] again. Returns at once in every other product mode, where the model already
   * lives on the backend.
   */
  private suspend fun synchronizeWithTheLocalModelOfALightSession(frontendRecentFilesModel: FrontendRecentFilesModel, project: Project) {
    // Strictly LIGHT, not isLight: LIGHT_WITH_RD_CONNECTION already awaits its backend. See awaitWithLocalFallback.
    if (IdeProductMode.getInstance().currentMode != ProductMode.LIGHT) return

    // TODO IJPL-252054 watch the product mode of the applied plugin set instead of the connection, once the platform
    //  publishes that mode as a flow. `PluginManagerCore.currentInitContextFlow` on the branch
    //  `khbminus/light-2/monolith-product-mode` is that flow, and it also covers a mode change of any other origin.
    coroutineScope {
      val subscriptions = launch { synchronizeWithTheModel(frontendRecentFilesModel, project) }
      LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<FileSwitcherApi>())
      LOG.debug("The light session gained a backend, restart the recent files synchronisation against it")
      subscriptions.cancelAndJoin()
    }
  }

  private suspend fun synchronizeWithTheModel(frontendRecentFilesModel: FrontendRecentFilesModel, project: Project) {
    coroutineScope {
      launch {
        LOG.debug("Subscribe to backend recently opened files updates")
        durable {
          frontendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_OPENED)
        }
      }
      launch {
        LOG.debug("Subscribe to backend recently edited files updates")
        durable {
          frontendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_EDITED)
        }
      }
      launch {
        LOG.debug("Subscribe to backend recently opened unpinned files updates")
        durable {
          frontendRecentFilesModel.subscribeToBackendRecentFilesUpdates(RecentFileKind.RECENTLY_OPENED_UNPINNED)
        }
      }

      launch {
        LOG.debug("Fetch initial recently opened files data")
        durable {
          frontendRecentFilesModel.fetchInitialData(RecentFileKind.RECENTLY_OPENED, project)
        }
      }
      launch {
        LOG.debug("Fetch initial recently edited files data")
        durable {
          frontendRecentFilesModel.fetchInitialData(RecentFileKind.RECENTLY_EDITED, project)
        }
      }
      launch {
        LOG.debug("Fetch initial recently opened unpinned files data")
        durable {
          frontendRecentFilesModel.fetchInitialData(RecentFileKind.RECENTLY_OPENED_UNPINNED, project)
        }
      }
    }
  }
}
