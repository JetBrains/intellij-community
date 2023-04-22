// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.indexing.IndexingBundle
import java.util.concurrent.atomic.AtomicReference

class DumbServiceScanningListener(private val project: Project) : FilesScanningListener {
  private val token = AtomicReference<AccessToken?>()
  override fun filesScanningStarted() {
    val suspender = (DumbService.getInstance(project) as DumbServiceImpl).guiSuspender
    val newToken = suspender.heavyActivityStarted(IndexingBundle.message("progress.indexing.scanning"))
    val oldToken = token.getAndSet(newToken)
    if (oldToken != null) {
      oldToken.finish()
      LOG.error("oldToken was not cleared properly")
    }
  }

  override fun filesScanningFinished() {
    token.getAndSet(null)?.finish() ?: LOG.error("oldToken should not be null")
  }

  companion object {
    private val LOG = logger<DumbServiceScanningListener>()
  }
}
