// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl

import java.nio.file.Path

internal data class DirectoryChangeEvent(
  val eventType: EventType,
  val isDirectory: Boolean,
  val path: Path?,
  val count: Int,
  val rootPath: Path?,
) {
  internal enum class EventType {
    CREATE,
    MODIFY,
    DELETE,
    OVERFLOW,
  }
}
