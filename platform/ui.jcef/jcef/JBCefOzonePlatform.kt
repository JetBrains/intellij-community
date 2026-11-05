// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class JBCefOzonePlatform(val registryOption: String) {
  X11("x11"),
  WAYLAND("wayland")
}