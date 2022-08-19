// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class DistributionPluginDescriptor : GradleStaticPluginDescriptor {
  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.DISTRIBUTION

  override val dependencies: List<GradleStaticPluginEntry> = listOf(
    GradleStaticPluginEntry.BASE
  )

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    extension("distributions", "org.gradle.api.distribution.DistributionContainer")
    task("distZip", "Bundles the project as a distribution.")
    task("distTar", "Bundles the project as a distribution.")
    task("installDist", "Installs the project as a distribution as-is.")
    task("assembleDist", "Assembles the distribution.")
  }
}