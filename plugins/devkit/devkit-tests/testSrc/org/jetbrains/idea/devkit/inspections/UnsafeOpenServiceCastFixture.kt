// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * The fixture of the [UnsafeOpenServiceCastInspection] tests. Create it with [unsafeOpenServiceCastFixture].
 *
 * The fixture enables [UnsafeOpenServiceCastInspection]. The project has the stubs of the service API.
 */
class UnsafeOpenServiceCastFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * Checks the highlighting of [codeFilePath] and [xmlFilePath] together.
   * Call it from any thread. It checks the highlighting on the EDT, because it opens an editor for each file.
   */
  fun doTest(codeFilePath: String, xmlFilePath: String) {
    runInEdtAndWait {
      fixture.testHighlightingAllFiles(true, false, false, codeFilePath, xmlFilePath)
    }
  }
}

/**
 * Creates a [UnsafeOpenServiceCastFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun unsafeOpenServiceCastFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<UnsafeOpenServiceCastFixture> = testFixture("unsafeOpenServiceCastFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(UnsafeOpenServiceCastInspection())
  fixture.addClass("""
    package com.intellij.openapi.components;

    public interface ComponentManager {
      <T> T getService(@org.jetbrains.annotations.NotNull Class<T> serviceClass);
      <T> T getServiceIfCreated(@org.jetbrains.annotations.NotNull Class<T> serviceClass);
    }
  """)
  fixture.addClass("""
    package com.intellij.openapi.project;

    import com.intellij.openapi.components.ComponentManager;

    public interface Project extends ComponentManager {}
  """)
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

    import com.intellij.openapi.components.ComponentManager;

    public interface Application extends ComponentManager {}
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

      @Attribute("open")
      public boolean open;

      @Attribute("overrides")
      public boolean overrides;
    }
  """)
  fixture.addClass("""
    package com.intellij.openapi.components;

    public @interface Service {
      Level[] value() default Level.APP;

      enum Level { APP, PROJECT }
    }
  """)
  fixture.addClass("""
    package kotlin.reflect;

    public class KClass<T> {
      public Class<T> java;
    }
  """)
  initialized(UnsafeOpenServiceCastFixture(fixture, fileExtension)) {}
}
