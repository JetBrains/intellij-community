// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope

private class ValueLookupManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // in production mode we can use coroutineScope { ... } here, since `execute` will be called canceled on project close,
    // but it won't happen in tests mode, so we need to have a separate service for the subscription.
    project.serviceAsync<ValueLookupManagerSubscriptionService>().subscribe()
  }
}

@Service(Service.Level.PROJECT)
private class ValueLookupManagerSubscriptionService(private val project: Project, private val cs: CoroutineScope) {
  fun subscribe() {
    subscribeForDebuggingStart(cs, project) {
      ValueLookupManager.getInstance(project).startListening()
    }

    subscribeForValueHintHideRequest(cs, project) {
      ValueLookupManager.getInstance(project).hideHint()
    }
  }
}