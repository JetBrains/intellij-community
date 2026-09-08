// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class DependencyKind {
  /** A `<dependencies><module>` entry. */
  MODULE,

  /** A `<dependencies><plugin>` entry. */
  PLUGIN,

  /** A legacy `<depends>` entry. */
  DEPENDS,
}
