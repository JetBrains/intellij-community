// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class WarPluginDescriptor : GradleStaticPluginDescriptor {
  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.WAR

  override val dependencies: List<GradleStaticPluginEntry> = listOf(
    GradleStaticPluginEntry.JAVA
  )

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    task("war", "Generates a war archive with all the compiled classes, the web-app content and the libraries.")
    configuration("providedCompile", "Additional compile classpath for libraries that should not be part of the WAR archive.")
    configuration("providedRuntime", "Additional runtime classpath for libraries that should not be part of the WAR archive.")
  }
}