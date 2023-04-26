// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.yield

@RequiresEdt
fun executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project: Project) {
  repeat(3) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    runBlockingModal(project, "") {
      yield()
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}