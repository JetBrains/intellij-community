// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [ListenerImplementationMustNotBeDisposableInspection]. The project has the stubs of `Disposable` and `BaseListenerInterface`, and `plugin.xml` of the test data is the plugin descriptor.
 */
@JvmOverloads
fun listenerImplementationMustNotBeDisposableFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<PluginModuleFixture> = testFixture("listenerImplementationMustNotBeDisposableFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath, fileExtension).init()
  val fixture = pluginModule.fixture
  fixture.addClass(
    """      
    package com.intellij.openapi;

    public interface Disposable { }
    """.trimIndent()
  )
  fixture.addClass(
    """      
    public interface BaseListenerInterface { }
    """.trimIndent()
  )
  pluginModule.setPluginXml("plugin.xml")
  fixture.enableInspections(ListenerImplementationMustNotBeDisposableInspection())
  initialized(pluginModule) {}
}
