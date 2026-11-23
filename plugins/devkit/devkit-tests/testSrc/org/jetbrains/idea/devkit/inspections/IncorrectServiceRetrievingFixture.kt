// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * The fixture of the [IncorrectServiceRetrievingInspection] tests. Create it with [incorrectServiceRetrievingFixture].
 *
 * The fixture enables [IncorrectServiceRetrievingInspection]. The project has the stubs of `ComponentManager`, `Project`, `Application` and `KClass`.
 */
class IncorrectServiceRetrievingFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * Adds the stub of `Service` and checks the highlighting of `<test name>.<file extension>`.
   * With [isAppLevelDefault], the default level of `Service` is `APP`. Otherwise, the default level is empty.
   */
  fun doTest(isAppLevelDefault: Boolean) {
    if (isAppLevelDefault) {
      fixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default Level.APP;

        enum Level { APP, PROJECT }
      }
      """)
    }
    else {
      fixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default {};

        enum Level { APP, PROJECT }
      }
      """)
    }
    doTest()
  }
}

/**
 * Creates a [IncorrectServiceRetrievingFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun incorrectServiceRetrievingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<IncorrectServiceRetrievingFixture> = testFixture("incorrectServiceRetrievingFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(IncorrectServiceRetrievingInspection())
  fixture.addClass(
    """
    package com.intellij.openapi.components;

    public interface ComponentManager {
      <T> T getService(@NotNull Class<T> serviceClass);
      <T> T getServiceIfCreated(@NotNull Class<T> serviceClass);
    }
    """)
  fixture.addClass(
    """
    package com.intellij.openapi.project;

    import com.intellij.openapi.components.ComponentManager;

    public interface Project extends ComponentManager {}
    """)
  fixture.addClass(
    """
    package com.intellij.openapi.application;

    public final class ApplicationManager {
      private static Application ourApplication;

      public static Application getApplication() {
        return ourApplication;
      }      
    }
    """)
  fixture.addClass(
    """
    package com.intellij.openapi.application;

    import com.intellij.openapi.components.ComponentManager;

    public interface Application extends ComponentManager {}
    """)
  fixture.addClass(
    """
    package kotlin.reflect;

    public class KClass<T> {
      public Class<T> java;
    }
    """)
  initialized(IncorrectServiceRetrievingFixture(fixture, fileExtension)) {}
}
