// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.impl

import org.jetbrains.plugins.gradle.service.resolve.staticModel.api.GradleStaticPluginNamespace

internal class GradleStaticPluginNamespaceImpl : GradleStaticPluginNamespace {
  val tasks : List<GradleStaticTask> get() = myTasks
  val extensions : List<GradleStaticExtension> get() = myExtensions
  val configurations : List<GradleStaticConfiguration> get() = myConfigurations

  private val myTasks : MutableList<GradleStaticTask> = mutableListOf()
  private val myExtensions : MutableList<GradleStaticExtension> = mutableListOf()
  private val myConfigurations: MutableList<GradleStaticConfiguration> = mutableListOf()

  override fun task(name: String, description: String?, configurationParameters: Map<String, String>) {
    myTasks.add(GradleStaticTask(name, description, configurationParameters))
  }

  override fun extension(name: String, typeFqn: String, description: String?) {
    myExtensions.add(GradleStaticExtension(name, typeFqn, description))
  }

  override fun configuration(name: String, description: String?) {
    myConfigurations.add(GradleStaticConfiguration(name, description))
  }
}