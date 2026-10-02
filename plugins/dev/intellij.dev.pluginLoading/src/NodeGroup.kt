// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
enum class NodeGroup {
  CONTENT_MODULES,
  DEPENDS_CONFIGS,
  DEPENDENCIES,
  EXCLUSION_CHAIN,
  DESCRIPTOR_READ_ERRORS,
  ;

  /** The title of the group row. */
  val title: @Nls String
    get() = when (this) {
      CONTENT_MODULES -> DevPluginLoadingBundle.message("plugin.loading.state.group.content.modules")
      DEPENDS_CONFIGS -> DevPluginLoadingBundle.message("plugin.loading.state.group.depends.configs")
      DEPENDENCIES -> DevPluginLoadingBundle.message("plugin.loading.state.group.dependencies")
      EXCLUSION_CHAIN -> DevPluginLoadingBundle.message("plugin.loading.state.group.exclusion.chain")
      DESCRIPTOR_READ_ERRORS -> DevPluginLoadingBundle.message("plugin.loading.state.group.read.errors")
    }
}
