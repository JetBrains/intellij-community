// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.LightServiceMigrationCodeInspection
import org.jetbrains.idea.devkit.inspections.LightServiceMigrationXMLInspection

/**
 * The fixture of the light service migration inspection tests. Create it with [lightServiceMigrationFixture].
 *
 * The fixture enables the light service migration inspections for the code and for `plugin.xml`. The project has the stubs of the service API.
 */
class LightServiceMigrationFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * The name of the quick fix that converts a service to an application-level light service.
   */
  val convertToLightServiceAppLevel: String = DevKitBundle.message("inspection.light.service.migration.app.level.fix")

  /**
   * The name of the quick fix that converts a service to a project-level light service.
   */
  val convertToLightServiceProjectLevel: String = DevKitBundle.message("inspection.light.service.migration.project.level.fix")

  /**
   * The extension of the XML test data files.
   */
  val xmlExtension: String = "xml"

  /**
   * Checks the highlighting of [codeFilePath] and [xmlFilePath] together.
   * Call it from any thread. It checks the highlighting on the EDT, because it opens an editor for each file.
   */
  fun doTest(codeFilePath: String, xmlFilePath: String) {
    runInEdtAndWait {
      fixture.testHighlightingAllFiles(true, false, false, codeFilePath, xmlFilePath)
    }
  }

  /**
   * Checks the highlighting of `<testName>.<fileExtension>`, applies the quick fix [fixName] and checks `<testName>_after.<fileExtension>`.
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doTest(fixName: String, fileExtension: String, testName: String) {
    val fileNameBefore = "$testName.$fileExtension"
    val fileNameAfter = testName + "_after." + fileExtension
    fixture.testHighlighting(fileNameBefore)
    runInEdtAndWait {
      val intention = fixture.findSingleIntention(fixName)
      fixture.launchAction(intention)
      fixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
    }
  }

  /**
   * Returns `<test name>.<file extension>`.
   */
  fun getCodeFilePath(): String {
    return fixture.getTestName(false) + "." + fileExtension
  }

  /**
   * Returns `<test name with a lower case first letter>.xml`.
   */
  fun getXmlFilePath(): String {
    return fixture.getTestName(true) + "." + xmlExtension
  }
}

/**
 * Creates a [LightServiceMigrationFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun lightServiceMigrationFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightServiceMigrationFixture> = testFixture("lightServiceMigrationFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.openapi.components;
    public interface PersistentStateComponent<T> { }
  """)
  fixture.addClass("""
    package com.intellij.util.xmlb.annotations;
    
    public @interface Attribute { String value() default ""; }
  """)
  fixture.addClass("""
    package com.intellij.openapi.components;
    
    import com.intellij.util.xmlb.annotations.Attribute;

    public class ServiceDescriptor {
      @Attribute("serviceImplementation")
      public String serviceImplementation;

      @Attribute("serviceInterface")
      public String serviceInterface;

      @Attribute("preload")
      public PreloadMode preload;

      @Attribute("configurationSchemaKey")
      public String configurationSchemaKey;

      public enum PreloadMode {}
    }
  """)
  fixture.addClass("""
      package com.intellij.openapi.components;
      public @interface Service {}
    """
  )
  fixture.addClass("""
    package com.intellij.openapi.application;

    public final class ApplicationManager {

      private static Application ourApplication;

      public static Application getApplication() {
        return ourApplication;
      }  
    }
  """)
  fixture.addClass("""
    package com.intellij.openapi.application;

    public interface Application {
      boolean isUnitTestMode();
      boolean isHeadlessEnvironment();
    }
  """)
  fixture.enableInspections(LightServiceMigrationXMLInspection::class.java,
                              LightServiceMigrationCodeInspection::class.java)
  initialized(LightServiceMigrationFixture(fixture, fileExtension)) {}
}
