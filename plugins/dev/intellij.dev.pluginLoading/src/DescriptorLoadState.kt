// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import org.jetbrains.annotations.ApiStatus

/**
 * The load state of a single plugin descriptor, a `<depends>` config or a content module.
 */
@ApiStatus.Internal
enum class DescriptorLoadState {
  /** The descriptor is resolved and it has a class loader. */
  LOADED,

  /** The descriptor is resolved, but it has no class loader. This state marks a problem. */
  RESOLVED_WITHOUT_CLASS_LOADER,

  /** The plugin set resolution excluded the descriptor. */
  EXCLUDED,

  /** The plugin never entered the resolution, so the plugin set holds no per-descriptor reason. */
  NOT_A_CANDIDATE,
  ;

  val isProblem: Boolean get() = this != LOADED
}
