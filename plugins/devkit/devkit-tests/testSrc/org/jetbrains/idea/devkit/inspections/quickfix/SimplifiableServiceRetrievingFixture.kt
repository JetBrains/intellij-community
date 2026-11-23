// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.SimplifiableServiceRetrievingInspection

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [SimplifiableServiceRetrievingInspection]. The project has the stubs of the service API.
 */
@JvmOverloads
fun simplifiableServiceRetrievingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("simplifiableServiceRetrievingFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(SimplifiableServiceRetrievingInspection())
  fixture.addClass(
    """
    package com.intellij.openapi.components;

    public @interface Service {
      Level[] value() default Level.APP;

      enum Level { APP, PROJECT }
    }
    """)
  fixture.addClass(
    """
    package com.intellij.openapi.components;

    public interface ComponentManager {
      <T> T getService(@NotNull Class<T> serviceClass);
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
    package kotlinx.coroutines;

    public interface CoroutineScope {}
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
  fixture.addClass(
    """
    package kotlin.jvm;

    public @interface JvmStatic { }
    """
  )
  initialized(devKit) {}
}
