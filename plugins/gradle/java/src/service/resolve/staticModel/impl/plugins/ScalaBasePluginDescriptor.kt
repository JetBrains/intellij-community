// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.plugins

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginDescriptor
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginEntry
import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class ScalaBasePluginDescriptor : GradleStaticPluginDescriptor {
  override val pluginEntry: GradleStaticPluginEntry = GradleStaticPluginEntry.SCALA_BASE

  override val dependencies: List<GradleStaticPluginEntry> = listOf(
    GradleStaticPluginEntry.JAVA_BASE,
  )

  override fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion) {
    extension("scalaRuntime", "org.gradle.api.tasks.ScalaRuntime")
    extension("scala", "org.gradle.api.plugins.scala.ScalaPluginExtension")
    configuration("scalaCompilerPlugins")
    configuration("zinc", "The Zinc incremental compiler to be used for this Scala project.")
    configuration("incrementalScalaAnalysisElements", "Incremental compilation analysis files.")
  }
}