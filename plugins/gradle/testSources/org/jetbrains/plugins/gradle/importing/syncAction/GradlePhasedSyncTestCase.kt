// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import java.util.concurrent.atomic.AtomicReference

abstract class GradlePhasedSyncTestCase : GradleProjectResolverTestCase() {

  private lateinit var syncErrorHandler: AtomicReference<(String, String?) -> Unit>
  private val defaultSyncErrorHandler = { errorMessage: String, errorDetails: String? ->
    super.handleImportFailure(errorMessage, errorDetails)
  }

  override fun setUp() {
    super.setUp()

    syncErrorHandler = AtomicReference(defaultSyncErrorHandler)
  }

  fun importProject(errorHandler: (String, String?) -> Unit) {
    syncErrorHandler.set(errorHandler)
    try {
      importProject()
    }
    finally {
      syncErrorHandler.set(defaultSyncErrorHandler)
    }
  }

  override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
    syncErrorHandler.get()(errorMessage, errorDetails)
  }
}