// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import java.util.concurrent.Future

interface XMixedModeDebugProcess {
  fun pauseMixedModeSession(): Future<Void>
  suspend fun resumeAndWait(): Boolean
}