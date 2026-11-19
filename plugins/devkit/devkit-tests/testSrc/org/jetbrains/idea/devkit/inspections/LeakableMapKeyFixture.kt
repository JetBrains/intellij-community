// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [basePath] is the test data path under the community directory.
 * The fixture enables [LeakableMapKeyInspection]. The project has the stubs of `Language`, `FileType` and `LanguageFileType`.
 */
@JvmOverloads
fun leakableMapKeyFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<PluginModuleFixture> = testFixture("leakableMapKeyFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath).init()
  val fixture = pluginModule.fixture
  fixture.addClass("package com.intellij.lang; public abstract class Language {}")
  fixture.addClass("package com.intellij.openapi.fileTypes; public interface FileType {}")
  fixture.addClass("package com.intellij.openapi.fileTypes; public abstract class LanguageFileType implements FileType {}")
  fixture.enableInspections(LeakableMapKeyInspection::class.java)
  initialized(pluginModule) {}
}
