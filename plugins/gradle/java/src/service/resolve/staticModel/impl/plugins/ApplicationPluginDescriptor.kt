// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class ApplicationPluginDescriptor : GradleStaticPluginDescriptor {
  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.APPLICATION

  override val dependencies: List<GradleStaticPluginEntry> = listOf(
    GradleStaticPluginEntry.JAVA,
    GradleStaticPluginEntry.DISTRIBUTION,
  )

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    extension("application", "org.gradle.api.plugins.JavaApplication")
    task("run", "Runs this project as a JVM application.")
    task("startScripts", "Creates OS specific scripts to run the project as a JVM application.")
  }
}