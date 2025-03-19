// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface RawProgressReporterHandle : AutoCloseable {

  val reporter: RawProgressReporter

  override fun close()
}
