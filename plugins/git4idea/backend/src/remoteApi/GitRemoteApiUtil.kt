// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.client.currentSession
import com.intellij.util.application

internal fun requireOwner() {
  check(application.currentSession.isOwner) { "Git API is available only for owner session" }
}