// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class BasePluginDescriptor : GradleStaticPluginDescriptor {

  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.BASE

  override val dependencies: List<GradleStaticPluginEntry> = listOf(GradleStaticPluginEntry.LIFECYCLE_BASE)

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    extension("base", "org.gradle.api.plugins.BasePluginExtension")
    configuration("archives", "Configuration for archive artifacts.")
    configuration("default", "Configuration for default artifacts.")
    extension("defaultArtifacts", "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet")
  }
}