// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class RegistryValueSource {
  /**
   * Values set by user via Registry UI.
   */
  USER,

  /**
   * Values set programmatically or loaded from configuration.
   */
  SYSTEM,

  /**
   * Used by cloud or IDE Services provisioning mechanisms.
   */
  MANAGER,
}