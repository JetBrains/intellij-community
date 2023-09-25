// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.gdpr.showDataSharingAgreement
import com.intellij.ide.gdpr.showEndUserAndDataSharingAgreements
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.platform.diagnostic.telemetry.impl.span
import kotlinx.coroutines.*

// On startup 2 dialogs must be shown:
// - gdpr agreement
// - eu(l)a
internal suspend fun loadEuaDocument(appInfoDeferred: Deferred<ApplicationInfoEx>): EndUserAgreement.Document? {
  val vendorAsProperty = System.getProperty("idea.vendor.name", "")
  if (if (vendorAsProperty.isEmpty()) !(appInfoDeferred.await()).isVendorJetBrains else vendorAsProperty != "JetBrains") {
    return null
  }
  else {
    val document = span("eua getting") { EndUserAgreement.getLatestDocument() }
    return if (span("eua is accepted checking") { document.isAccepted }) null else document
  }
}

internal suspend fun prepareShowEuaIfNeededTask(document: EndUserAgreement.Document?,
                                                appInfoDeferred: Deferred<ApplicationInfoEx>,
                                                asyncScope: CoroutineScope): (suspend () -> Boolean)? {
  val updateCached = asyncScope.launch(CoroutineName("eua cache updating") + Dispatchers.IO) {
    EndUserAgreement.updateCachedContentToLatestBundledVersion()
  }

  suspend fun prepareAndExecuteInEdt(task: () -> Unit) {
    updateCached.join()
    withContext(RawSwingDispatcher) {
      task()
    }
  }

  return span("eua showing") {
    if (document != null) {
      return@span {
        prepareAndExecuteInEdt {
          showEndUserAndDataSharingAgreements(document)
        }
        true
      }
    }
    appInfoDeferred.await() //ConsentOptions uses ApplicationInfo inside, so we need to load it first
    if (ConsentOptions.needToShowUsageStatsConsent()) {
      updateCached.join()
      return@span {
        prepareAndExecuteInEdt {
          showDataSharingAgreement()
        }
        false
      }
    }
    else null
  }
}