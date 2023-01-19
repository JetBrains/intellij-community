// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.diagnostic.runActivity
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.gdpr.showDataSharingAgreement
import com.intellij.ide.gdpr.showEndUserAndDataSharingAgreements
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.RawSwingDispatcher
import kotlinx.coroutines.*
import javax.swing.UIManager

// On startup 2 dialogs must be shown:
// - gdpr agreement
// - eu(l)a
internal suspend fun loadEuaDocument(appInfoDeferred: Deferred<ApplicationInfoEx>): EndUserAgreement.Document? {
  val vendorAsProperty = System.getProperty("idea.vendor.name", "")
  if (if (vendorAsProperty.isEmpty()) !(appInfoDeferred.await()).isVendorJetBrains else vendorAsProperty != "JetBrains") {
    return null
  }
  else {
    val document = runActivity("eua getting") { EndUserAgreement.getLatestDocument() }
    return if (runActivity("eua is accepted checking") { document.isAccepted }) null else document
  }
}

internal suspend fun prepareShowEuaIfNeededTask(document: EndUserAgreement.Document?, asyncScope: CoroutineScope): (suspend () -> Boolean)? {
  val updateCached = asyncScope.launch(CoroutineName("eua cache updating") + Dispatchers.IO) {
    EndUserAgreement.updateCachedContentToLatestBundledVersion()
  }

  suspend fun prepareAndExecuteInEdt(task: () -> Unit) {
    updateCached.join()
    withContext(RawSwingDispatcher) {
      if (UIManager.getLookAndFeel() !is IntelliJLaf) {
        UIManager.setLookAndFeel(IntelliJLaf())
      }
      task()
    }
  }

  return runActivity("eua showing") {
    when {
      document != null -> {
        return {
          prepareAndExecuteInEdt {
            showEndUserAndDataSharingAgreements(document)
          }
          true
        }
      }
      ConsentOptions.needToShowUsageStatsConsent() -> {
        updateCached.join()
        return {
          prepareAndExecuteInEdt {
            showDataSharingAgreement()
          }
          false
        }
      }
      else -> null
    }
  }
}