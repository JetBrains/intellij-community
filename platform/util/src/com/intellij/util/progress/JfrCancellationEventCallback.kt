// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.progress

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface JfrCancellationEventCallback {
  fun nonCanceledSectionInvoked()
  fun cancellableSectionInvoked(wasCanceled: Boolean)
}