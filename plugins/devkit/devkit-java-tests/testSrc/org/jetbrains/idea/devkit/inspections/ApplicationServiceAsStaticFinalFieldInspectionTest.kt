// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import kotlin.io.path.*

@TestDataPath("\$CONTENT_ROOT/testData/inspections/applicationServiceAsStaticFinalField")

class ApplicationServiceAsStaticFinalFieldInspectionTest : PluginModuleTestCase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/applicationServiceAsStaticFinalField"

  override fun setUp() {
    super.setUp()
    addPlatformClasses()
    myFixture.enableInspections(ApplicationServiceAsStaticFinalFieldInspection::class.java)
  }

  private fun addPlatformClasses() {
    myFixture.addClass(
      //language=JAVA
      """
      package com.intellij.openapi.components;
      
      public @interface Service {
        Level[] value() default { };
      
        enum Level {
          APP, PROJECT
        }
      }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package org.jetbrains.annotations;
      
      public @interface NotNull { }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package com.intellij.openapi.components;
      
      public interface ComponentManager {
        <T> T getService(@NotNull Class<T> serviceClass);
      }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package com.intellij.openapi.project;
      
      import com.intellij.openapi.components.ComponentManager;
      
      public interface Project extends ComponentManager { }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package com.intellij.openapi.application;
      
      public class ApplicationManager {
        protected static Application ourApplication;
      
        public static Application getApplication() {
          return ourApplication;
        }      
      }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package com.intellij.openapi.application;
      
      import com.intellij.openapi.components.ComponentManager;
      
      public interface Application extends ComponentManager { }
      """.trimIndent()
    )
  }

  private fun getServiceDeclarationPaths(namePrefix: String = ""): Array<String> {
    return Path(testDataPath, "serviceDeclarations")
      .listDirectoryEntries()
      .filter { it.name.startsWith(namePrefix) }
      .map { it.relativeTo(Path(testDataPath)).toString() }
      .toTypedArray()
  }

  fun testNonServicesInFields() {
    myFixture.testHighlighting("NonServicesInFields.java", *getServiceDeclarationPaths("NonService"))
  }

  fun testRegisteredServicesInFields() {
    setPluginXml("plugin.xml")
    myFixture.testHighlighting("RegisteredServicesInFields.java", *getServiceDeclarationPaths("Registered"))
  }

  fun testLightServicesInFields() {
    myFixture.testHighlighting("LightServicesInFields.java", *getServiceDeclarationPaths("LightService"))
  }

}
