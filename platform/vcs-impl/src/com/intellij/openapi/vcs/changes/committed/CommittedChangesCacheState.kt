// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.components.BaseState

internal class CommittedChangesCacheState : BaseState() {
  var initialCount: Int by property(500)
  var initialDays: Int by property(90)
  var refreshInterval: Int by property(30)
  var isRefreshEnabled: Boolean by property(false)
}
