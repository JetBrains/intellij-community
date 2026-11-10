// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserServiceImpl
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeature
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertTrue

class PluginAdvertiserRescanTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule: ProjectRule = ProjectRule()
  }

  /**
   * A rescan after a change of the project dependencies suggests plugins for the dependencies only.
   * A feature that the project open already advertised, such as an unknown run configuration type, does not come back.
   */
  @Test
  fun aRescanAdvertisesNoFeatureOtherThanADependency() = runBlocking {
    val project = projectRule.project
    val collector = UnknownFeaturesCollector.getInstance(project)
    val runConfiguration = UnknownFeature("com.intellij.configurationType", "Run Configuration", "UNKNOWN_RUN_CONFIGURATION_TYPE")
    collector.registerUnknownFeature(runConfiguration)
    val scope = childScope("plugin advertiser service")
    try {
      val service = RecordingPluginAdvertiserService(project, scope)
      val rescanned = CompletableDeferred<Unit>()
      service.rescanDependencies { rescanned.complete(Unit) }
      rescanned.await()

      assertTrue(runConfiguration !in service.advertised, "the rescan advertised ${service.advertised}")
    }
    finally {
      scope.cancel()
      collector.unregisterUnknownFeature(runConfiguration)
    }
  }

  private class RecordingPluginAdvertiserService(project: Project, cs: CoroutineScope) : PluginAdvertiserServiceImpl(project, cs) {
    val advertised: MutableList<UnknownFeature> = mutableListOf()

    override suspend fun run(customPlugins: List<PluginNode>, unknownFeatures: Collection<UnknownFeature>, includeIgnored: Boolean) {
      advertised.addAll(unknownFeatures)
    }
  }
}
