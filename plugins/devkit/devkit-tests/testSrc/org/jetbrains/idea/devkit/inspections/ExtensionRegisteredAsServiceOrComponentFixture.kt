// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [ExtensionRegisteredAsServiceOrComponentInspection]. The project has the stubs of the extension and service API.
 */
@JvmOverloads
fun extensionRegisteredAsServiceOrComponentFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<PluginModuleFixture> = testFixture("extensionRegisteredAsServiceOrComponentFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath, fileExtension).init()
  val fixture = pluginModule.fixture
  fixture.addClass(
    //language=java
    """
      package com.intellij.openapi.extensions; 
      
      public class ExtensionPointName<T> { 
        public ExtensionPointName(String name) { }
      }
    """.trimIndent()
  )
  fixture.addClass(
    //language=java
    """
      import com.intellij.openapi.extensions.ExtensionPointName;
      
      public interface MyExtension {
        ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
      }
    """.trimIndent()
  )
  fixture.addClass(
    //language=java
    """
      package com.intellij.openapi.components;
      
      public @interface Service { }
    """.trimIndent()
  )
  fixture.addClass(
    //language=java
    """
      package com.intellij.openapi.components;
      
      public interface BaseComponent { }
    """.trimIndent()
  )
  fixture.addClass(
    //language=java
    """
      package com.intellij.util.xmlb.annotations;
      
      public @interface Attribute { }
    """.trimIndent()
  )
  fixture.enableInspections(ExtensionRegisteredAsServiceOrComponentInspection::class.java)
  initialized(pluginModule) {}
}
