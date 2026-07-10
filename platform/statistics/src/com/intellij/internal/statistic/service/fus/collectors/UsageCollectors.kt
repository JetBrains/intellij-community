// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object UsageCollectors {
  @ApiStatus.Internal
  @JvmField
  val APPLICATION_EP_NAME: ExtensionPointName<UsageCollectorBean> = ExtensionPointName("com.intellij.statistics.applicationUsagesCollector")

  @ApiStatus.Internal
  @JvmField
  val PROJECT_EP_NAME: ExtensionPointName<UsageCollectorBean> = ExtensionPointName("com.intellij.statistics.projectUsagesCollector")

  @ApiStatus.Internal
  @JvmField
  val COUNTER_EP_NAME: ExtensionPointName<CounterUsageCollectorEP> = ExtensionPointName("com.intellij.statistics.counterUsagesCollector")

  internal fun getApplicationCollectors(
    invoker: UsagesCollectorConsumer,
    allowedOnStartupOnly: Boolean,
  ): Collection<ApplicationUsagesCollector> {
    if (isCalledFromPlugin(invoker)) {
      return emptyList()
    }

    if (!allowedOnStartupOnly) {
      return getAllApplicationCollectors()
    }

    return APPLICATION_EP_NAME.extensionList.asSequence()
      .filter { it.allowOnStartup == true }
      .mapNotNull { it.getCollectorIfApplicable() as ApplicationUsagesCollector? }
      .toList()
  }

  private fun getAllApplicationCollectors(): Collection<ApplicationUsagesCollector> {
    return APPLICATION_EP_NAME.extensionList.asSequence()
      .mapNotNull { it.getCollectorIfApplicable() as ApplicationUsagesCollector? }
      .toList()
  }

  internal fun getProjectCollectors(invoker: UsagesCollectorConsumer): Collection<ProjectUsagesCollector> {
    if (isCalledFromPlugin(invoker)) {
      return emptyList()
    }

    return PROJECT_EP_NAME.extensionList.asSequence()
      .mapNotNull { it.getCollectorIfApplicable() as ProjectUsagesCollector? }
      .toList()
  }

  private fun isCalledFromPlugin(invoker: UsagesCollectorConsumer): Boolean {
    return invoker.javaClass.classLoader is PluginAwareClassLoader
  }
}