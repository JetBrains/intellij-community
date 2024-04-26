// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

internal sealed interface ProgressReporterHandle : AutoCloseable {

  val reporter: ProgressReporter

  override fun close()
}
