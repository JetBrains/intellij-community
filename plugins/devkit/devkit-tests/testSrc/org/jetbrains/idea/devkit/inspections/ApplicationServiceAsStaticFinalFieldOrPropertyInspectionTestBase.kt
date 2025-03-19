// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo

abstract class ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTestBase : LightDevKitInspectionFixTestBase()  {

  override fun setUp() {
    super.setUp()
    addPlatformClasses()
    myFixture.enableInspections(ApplicationServiceAsStaticFinalFieldOrPropertyInspection())
  }

  private fun addPlatformClasses() {
    myFixture.addClass(
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

    myFixture.addClass(
      """
      package org.jetbrains.annotations;
      
      public @interface NotNull { }
      """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.components;
      
      public interface ComponentManager {
        <T> T getService(@NotNull Class<T> serviceClass);
      }
      """.trimIndent()
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.project;
      
      import com.intellij.openapi.components.ComponentManager;
      
      public interface Project extends ComponentManager { }
      """.trimIndent()
    )

    myFixture.addClass(
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
      """
      package com.intellij.openapi.application;
      
      import com.intellij.openapi.components.ComponentManager;
      
      public interface Application extends ComponentManager { }
      """.trimIndent()
    )

    myFixture.addClass(
      """
      package inspections.wrapInSupplierFix;
      
      public @interface MyAnnotation { }
      """.trimIndent()
    )

    myFixture.addClass(
      """
      package java.util.function;
      
      @FunctionalInterface
      public interface Supplier<T> {      
          T get();
      }
      """.trimIndent()
    )

    myFixture.addClass(
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

    myFixture.addClass(
      """
      package kotlin.reflect;

      public class KClass<T> {
        public Class<T> java;
      }
      """.trimIndent()
    )

    myFixture.configureByText(
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
  }

  private fun getServiceDeclarationPaths(namePrefix: String = ""): Array<String> {
    return Path(testDataPath, "serviceDeclarations")
      .listDirectoryEntries()
      .filter { it.name.startsWith(namePrefix) }
      .map { it.relativeTo(Path(testDataPath)).toString() }
      .toTypedArray()
  }

  protected open fun doHighlightTest() {
    myFixture.configureByFile("plugin.xml")
    myFixture.testHighlighting("${getTestName(false)}.$fileExtension", *getServiceDeclarationPaths())
  }

  protected fun doFixTest(fixName: String) {
    val (referencesFileNameBefore, referencesFileNameAfter) = getBeforeAfterFileNames(suffix = "references")
    myFixture.configureByFile(referencesFileNameBefore)
    doTest(fixName)
    myFixture.checkResultByFile(referencesFileNameBefore, referencesFileNameAfter, true)
  }

  private fun getBeforeAfterFileNames(testName: String = getTestName(false), suffix: String? = null): Pair<String, String> {
    val resultName = testName + suffix?.let { "_$it" }.orEmpty()
    return "${resultName}.$fileExtension" to "${resultName}_after.$fileExtension"
  }
}
