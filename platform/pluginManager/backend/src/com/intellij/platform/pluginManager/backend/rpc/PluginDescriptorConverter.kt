// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.getTags
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

/**
 * Converts [com.intellij.ide.plugins.IdeaPluginDescriptor] to [PluginDto] for compatibility purposes
 */
@ApiStatus.Internal
@IntellijInternalApi
object PluginDescriptorConverter {

  fun toPluginDto(descriptor: IdeaPluginDescriptor): PluginDto {
    val pluginDto = PluginDto(
      name = descriptor.name,
      pluginId = descriptor.pluginId
    )

    with(pluginDto) {
      version = descriptor.version
      isBundled = descriptor.isBundled
      isDeleted = (descriptor as? IdeaPluginDescriptorImpl)?.isDeleted ?: false
      category = descriptor.category
      description = descriptor.description
      vendor = descriptor.vendor
      changeNotes = descriptor.changeNotes
      productCode = descriptor.productCode
      isEnabled = descriptor.isEnabled
      isLicenseOptional = descriptor.isLicenseOptional
      releaseVersion = descriptor.releaseVersion
      displayCategory = descriptor.displayCategory
      releaseDate = descriptor.releaseDate?.toInstant()?.toEpochMilli()

      descriptor.dependencies.forEach { dependency ->
        addDependency(dependency.pluginId, dependency.isOptional)
      }

      dependencyNames = descriptor.dependencies.map { it.pluginId.idString }

      tags = descriptor.getTags()

      source = PluginSource.REMOTE
      allowBundledUpdate = descriptor.allowBundledUpdate()
    }

    return pluginDto
  }
}