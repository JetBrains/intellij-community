// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.inspections.internal.StatisticsCollectorNotRegisteredInspection

/**
 * The fixture of the [StatisticsCollectorNotRegisteredInspection] tests. Create it with [statisticsCollectorNotRegisteredFixture].
 *
 * The fixture enables [StatisticsCollectorNotRegisteredInspection]. The project has the stubs of the usage collectors.
 * Each `doTest` method is the body of one test. [fileExtension] is the extension of the source files.
 * The methods that apply a quick fix can run on any thread. They apply the quick fix on the EDT.
 */
class StatisticsCollectorNotRegisteredFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  PluginModuleFixture(fixture, fileExtension) {
  fun doTestRegisteredStatisticsCollector() {
    setPluginXml("registeredCounterCollector-plugin.xml")
    fixture.testHighlighting("RegisteredCounterCollector.$fileExtension")
  }

  fun doTestRegisteredNestedStatisticsCollector() {
    setPluginXml("registeredNestedCounterCollector-plugin.xml")
    fixture.testHighlighting("RegisteredNestedCounterCollector.$fileExtension")
  }

  fun doTestUnregisteredCounterCollector() {
    setPluginXml("unregisteredCounterCollector-plugin.xml")
    fixture.testHighlighting("UnregisteredCounterCollector.$fileExtension")
    registerExtension("unregisteredCounterCollector-plugin_after.xml")
  }

  fun doTestUnregisteredProjectCollector() {
    setPluginXml("unregisteredProjectCollector-plugin.xml")
    fixture.testHighlighting("UnregisteredProjectCollector.$fileExtension")
    registerExtension("unregisteredProjectCollector-plugin_after.xml")
  }

  fun doTestUnregisteredNestedCountCollector() {
    setPluginXml("unregisteredCounterCollector-plugin.xml")
    fixture.testHighlighting("UnregisteredNestedCounterCollector.$fileExtension")
    registerExtension("unregisteredNestedCounterCollector-plugin_after.xml")
  }

  private fun registerExtension(pluginXmlAfter: String) {
    runInEdtAndWait {
      val registerAction = fixture.findSingleIntention("Register extension")
      fixture.launchAction(registerAction)
      fixture.checkResultByFile("META-INF/plugin.xml", pluginXmlAfter, true)
    }
  }
}

/**
 * Creates a [StatisticsCollectorNotRegisteredFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun statisticsCollectorNotRegisteredFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<StatisticsCollectorNotRegisteredFixture> = testFixture("statisticsCollectorNotRegisteredFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath, fileExtension).init()
  val fixture = pluginModule.fixture
  fixture.addClass("package com.intellij.internal.statistic.service.fus.collectors; public interface FeatureUsagesCollector {}")
  fixture.addClass("package com.intellij.internal.statistic.service.fus.collectors; public abstract class CounterUsagesCollector implements FeatureUsagesCollector {}")
  fixture.addClass("package com.intellij.internal.statistic.service.fus.collectors; public abstract class ProjectUsagesCollector implements FeatureUsagesCollector {}")
  fixture.enableInspections(StatisticsCollectorNotRegisteredInspection())
  initialized(StatisticsCollectorNotRegisteredFixture(fixture, fileExtension)) {}
}
