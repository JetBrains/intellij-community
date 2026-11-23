// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.relativeTo
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * The fixture of the [ApplicationServiceAsStaticFinalFieldOrPropertyInspection] tests. Create it with [applicationServiceAsStaticFinalFieldOrPropertyFixture].
 *
 * The fixture enables [ApplicationServiceAsStaticFinalFieldOrPropertyInspection]. The project has the stubs of the service API and the file `service.kt`.
 */
class ApplicationServiceAsStaticFinalFieldOrPropertyFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * Configures `plugin.xml` and checks the highlighting of `<test name>.<file extension>` with the files of `serviceDeclarations`.
   */
  fun doHighlightTest() {
    fixture.configureByFile("plugin.xml")
    fixture.testHighlighting("${fixture.getTestName(false)}.$fileExtension", *getServiceDeclarationPaths())
  }

  /**
   * Applies the quick fix [fixName] as [doTest] does. It also checks `<test name>_references_after.<file extension>`.
   */
  fun doFixTest(fixName: String) {
    val resultName = fixture.getTestName(false) + "_references"
    val referencesFileNameBefore = "$resultName.$fileExtension"
    fixture.configureByFile(referencesFileNameBefore)
    doTest(fixName)
    fixture.checkResultByFile(referencesFileNameBefore, "${resultName}_after.$fileExtension", true)
  }

  private fun getServiceDeclarationPaths(): Array<String> {
    return Path(fixture.testDataPath, "serviceDeclarations")
      .listDirectoryEntries()
      .map { it.relativeTo(Path(fixture.testDataPath)).toString() }
      .toTypedArray()
  }
}

/**
 * Creates a [ApplicationServiceAsStaticFinalFieldOrPropertyFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun applicationServiceAsStaticFinalFieldOrPropertyFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<ApplicationServiceAsStaticFinalFieldOrPropertyFixture> = testFixture("applicationServiceAsStaticFinalFieldOrPropertyFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass(
    """
    package com.intellij.openapi.components;
    
    public @interface Service {
      Level[] value() default Level.APP;
    
      enum Level {
        APP, PROJECT
      }
    }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package org.jetbrains.annotations;
    
    public @interface NotNull { }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package com.intellij.openapi.components;
    
    public interface ComponentManager {
      <T> T getService(@NotNull Class<T> serviceClass);
    }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package com.intellij.openapi.project;
    
    import com.intellij.openapi.components.ComponentManager;
    
    public interface Project extends ComponentManager { }
    """.trimIndent()
  )
  fixture.addClass(
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
  fixture.addClass(
    """
    package com.intellij.openapi.application;
    
    import com.intellij.openapi.components.ComponentManager;
    
    public interface Application extends ComponentManager { }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package inspections.wrapInSupplierFix;
    
    public @interface MyAnnotation { }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package java.util.function;
    
    @FunctionalInterface
    public interface Supplier<T> {      
        T get();
    }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package com.intellij.openapi.application;
    
    public final class CachedSingletonsRegistry {
    
        public static @NotNull <T> Supplier<T> lazy(@NotNull Supplier<? extends T> supplier) {
            // mocking the actual implementation 
            return supplier;
        }
    
    }
    """.trimIndent()
  )
  fixture.addClass(
    """
    package kotlin.reflect;

    public class KClass<T> {
      public Class<T> java;
    }
    """.trimIndent()
  )
  fixture.configureByText(
    "service.kt",
    //language=kotlin
    """
    package com.intellij.openapi.components

    import com.intellij.openapi.application.ApplicationManager

    inline fun <reified T : Any> service(): T {
      val serviceClass = T::class.java
      return ApplicationManager.getApplication().getService(serviceClass)
    }
    """.trimIndent()
  )
  fixture.enableInspections(ApplicationServiceAsStaticFinalFieldOrPropertyInspection())
  initialized(ApplicationServiceAsStaticFinalFieldOrPropertyFixture(fixture, fileExtension)) {}
}
