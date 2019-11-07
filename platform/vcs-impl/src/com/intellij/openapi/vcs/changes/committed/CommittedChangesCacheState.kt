// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.components.BaseState

class CommittedChangesCacheState : BaseState() {
  var initialCount: Int by property(500)
  var initialDays: Int by property(90)
  var refreshInterval: Int by property(30)
  var isRefreshEnabled: Boolean by property(false)
}