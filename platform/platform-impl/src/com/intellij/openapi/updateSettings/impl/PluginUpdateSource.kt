// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Describes the source from where a plugin is allowed to have updates. In general, it's the repository plugin was installed from.
 */
@Serializable
@ApiStatus.Internal
sealed interface PluginUpdateSource {
  val host: @NlsSafe String
  val isMarketplace: Boolean

  val presentableName: @Nls String

  /**
   * When sorting from most useful sources, use this field
   * The more it is, the more important this source is
   *
   * For example,`Marketplace` is more important than a custom repository
   */
  val semanticPriority: Int

  /**
   * This check is symmetrical, but not transitive.
   *
   * @return if a plugin with this update source may have updates from [other] update source
   * which describes a single repository or a group of repositories
   */
  fun canInstallUpdatesFrom(other: PluginUpdateSource): Boolean
}

@ApiStatus.Internal
fun PluginUpdateSource?.getPresentableName(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
  return this?.presentableName ?: IdeBundle.message("plugin.update.source.presentable.name.unknown")
}