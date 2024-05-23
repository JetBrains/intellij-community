// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

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
  val isVendorJetBrains = if (vendorAsProperty.isNotEmpty()) {
    vendorAsProperty == "JetBrains"
  }
  else {
    appInfoDeferred.await().isVendorJetBrains
  }
  if (!isVendorJetBrains) {
    return null
  }
  val document = span("eua getting") {
    EndUserAgreement.getLatestDocument()
  }
  return document.takeUnless {
    span("eua is accepted checking") {
      it.isAccepted
    }
  }
}

internal suspend fun prepareShowEuaIfNeededTask(document: EndUserAgreement.Document?,
                                                appInfoDeferred: Deferred<ApplicationInfoEx>,
                                                asyncScope: CoroutineScope): (suspend () -> Boolean)? {
  val updateCached = asyncScope.launch(CoroutineName("eua cache updating") + Dispatchers.IO) {
    EndUserAgreement.updateCachedContentToLatestBundledVersion()
  }

  suspend fun prepareAndExecuteInEdt(task: () -> Unit) {
    span("eua showing") {
      updateCached.join()
      withContext(RawSwingDispatcher) {
        task()
      }
    }
  }

  if (document != null) {
    return {
      prepareAndExecuteInEdt {
        showEndUserAndDataSharingAgreements(document)
      }
      true
    }
  }

  // ConsentOptions uses ApplicationInfo inside, so we need to load it first
  appInfoDeferred.await()
  if (ConsentOptions.needToShowUsageStatsConsent()) {
    return {
      prepareAndExecuteInEdt {
        showDataSharingAgreement()
      }
      false
    }
  }
  return null
}