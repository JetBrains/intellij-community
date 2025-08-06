// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.externalComponents.ExternalComponentSource
import com.intellij.ide.externalComponents.UpdatableExternalComponent
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@IntellijInternalApi
class UpdateChain internal constructor(
  val chain: List<BuildNumber>,
  val size: String?,
)

@ApiStatus.Internal
@IntellijInternalApi
sealed class PlatformUpdates {
  @ApiStatus.Internal
  @IntellijInternalApi
  data object Empty : PlatformUpdates()

  @ApiStatus.Internal
  @IntellijInternalApi
  data class Loaded @JvmOverloads internal constructor(
    val newBuild: BuildInfo,
    val updatedChannel: UpdateChannel,
    val patches: UpdateChain? = null,
  ) : PlatformUpdates()

  @ApiStatus.Internal
  @IntellijInternalApi
  data class ConnectionError internal constructor(val error: Exception) : PlatformUpdates()
}

/**
 * [allEnabled] - new versions of enabled plugins compatible with the specified build
 *
 * [allDisabled] - new versions of disabled plugins compatible with the specified build
 *
 * [incompatible] - plugins that would become incompatible and don't have updates compatible with the specified build
 */
// TODO separation into enabled and disabled as part of this class seems unnecessary
@ApiStatus.Internal
@IntellijInternalApi
data class PluginUpdates @JvmOverloads @VisibleForTesting constructor(
  val allEnabled: Collection<PluginDownloader> = emptyList(),
  val allDisabled: Collection<PluginDownloader> = emptyList(),
  val incompatible: Collection<IdeaPluginDescriptor> = emptyList(),
) {
  val all: List<PluginDownloader> by lazy {
    allEnabled + allDisabled
  }
}

// FIXME InternalPluginResults should not be exposed as a return value from non-internal API (or should be an interface instead) :(
//       this also applies to neighbor classes
@ApiStatus.Internal
@IntellijInternalApi
data class InternalPluginResults @JvmOverloads @VisibleForTesting constructor(
  val pluginUpdates: PluginUpdates,
  val pluginNods: Collection<PluginUiModel> = emptyList(),
  val errors: Map<String?, Exception> = emptyMap(),
)

@ApiStatus.Internal
@IntellijInternalApi
data class ExternalUpdate @JvmOverloads internal constructor(
  val source: ExternalComponentSource,
  val components: Collection<UpdatableExternalComponent> = emptyList(),
)

@ApiStatus.Internal
@IntellijInternalApi
data class ExternalPluginResults @JvmOverloads internal constructor(
  val externalUpdates: Collection<ExternalUpdate> = emptyList(),
  val errors: Map<ExternalComponentSource, Exception> = emptyMap(),
)
