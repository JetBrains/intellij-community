// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.api

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to describe Gradle plugin behavior statically
 */
@ApiStatus.Internal
internal interface GradleStaticPluginDescriptor {

  /**
   * The identifiable name of the plugin.
   * If [GradleStaticPluginEntry.pluginName] appears in `plugins { id 'pluginName' }`, then this plugin descriptor will be invoked to provide static support.
   */
  val pluginEntry: GradleStaticPluginEntry

  /**
   * The plugins that are loaded alongside with the current plugin.
   * Plugins with their [dependencies] must form a cycle-free graph.
   */
  val dependencies: List<GradleStaticPluginEntry>

  /**
   * Defines behavior of this plugin.
   */
  fun GradleStaticPluginNamespace.configure(gradleVersion: GradleVersion)

}