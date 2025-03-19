// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

internal fun reportModificationAttempt(): Nothing {
  throw UnsupportedOperationException("modifications aren't supported")
}