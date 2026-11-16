// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [ActionPresentationInstantiatedInCtorInspection]. The project has the stubs of `Icon` and `AnAction`.
 */
@JvmOverloads
fun actionPresentationInstantiatedInCtorFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<PluginModuleFixture> = testFixture("actionPresentationInstantiatedInCtorFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath, fileExtension).init()
  val fixture = pluginModule.fixture
  fixture.addClass("package javax.swing; public interface Icon {}")
  fixture.addClass("""
    package com.intellij.openapi.actionSystem;

    import javax.swing.Icon;
    import java.util.function.Supplier;

    public class AnAction {

      public AnAction() {}

      public AnAction(Icon icon) {}

      public AnAction(String text) {}

      public AnAction(Supplier<String> dynamicText) {}

      public AnAction(String text,
                      String description,
                      Icon icon) {}

      public AnAction(Supplier<String> dynamicText, Icon icon) {}

      public AnAction(Supplier<String> dynamicText,
                      Supplier<String> dynamicDescription,
                      Icon icon) {}
    }
    """)
  fixture.enableInspections(ActionPresentationInstantiatedInCtorInspection::class.java)
  initialized(pluginModule) {}
}
