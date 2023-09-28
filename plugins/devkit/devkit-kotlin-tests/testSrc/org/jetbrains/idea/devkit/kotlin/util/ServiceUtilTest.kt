// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.util

import com.intellij.openapi.components.Service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.LevelType
import org.jetbrains.idea.devkit.inspections.getLevelType
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import java.io.File
import kotlin.io.path.Path


private const val SERVICE_DECLARATIONS_DIR = "serviceDeclarations"
private const val SERVICE_USAGES_DIR = "serviceUsages"

@TestDataPath("\$CONTENT_ROOT/testData/util/serviceUtil")
class ServiceUtilTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "util/serviceUtil"

  override fun setUp() {
    super.setUp()
    addPlatformClasses()
    myFixture.configureByFile("plugin.xml")
  }

  // ##### Test services (defined both in Java and Kotlin) usages from Java #####
  fun testServiceLevelByAnnotationFromJava() {
    lightServiceTests("java").forEach(::serviceLevelByAnnotationTest)
  }


  fun testServiceLevelByClassFromJava() {
    (lightServiceTests("java") + registeredServiceTests("java")).forEach(::serviceLevelByClassTest)
  }

  // ##### Test services (defined both in Java and Kotlin) usages from Kotlin #####
  fun testServiceLevelByAnnotationFromKotlin() {
    lightServiceTests("kt").forEach(::serviceLevelByAnnotationTest)
  }


  fun testServiceLevelByClassFromKotlin() {
    (lightServiceTests("kt") + registeredServiceTests("kt")).forEach(::serviceLevelByClassTest)
  }

  fun testNonService() {
    nonServiceTests().forEach {
      serviceLevelByClassTest(it)
    }
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
      
      public final class ApplicationManager {
        private static Application ourApplication;
      
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
      package com.intellij.openapi.module;
      
      import com.intellij.openapi.components.ComponentManager;
      
      public interface Module extends ComponentManager { }
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

    myFixture.addClass(
      """
      package kotlin.jvm;

      public @interface JvmStatic { }
      """.trimIndent()
    )
  }

  private val serviceDeclarationPaths: Array<String>
    get() = getPaths(subpath = SERVICE_DECLARATIONS_DIR)

  private fun lightServiceTests(extension: String? = null): Array<String> {
    return getPaths(subpath = SERVICE_USAGES_DIR, extension = extension) { it.contains("Light") }
  }

  private fun registeredServiceTests(extension: String? = null): Array<String> {
    return getPaths(subpath = SERVICE_USAGES_DIR, extension = extension) { it.contains("Registered") }
  }

  private fun nonServiceTests(extension: String? = null): Array<String> {
    return getPaths(subpath = SERVICE_USAGES_DIR, extension = extension) { it.contains("NonService") }
  }

  private fun getExpectedServiceLevel(uClass: UClass): LevelType? {
    val comment = uClass.comments.firstOrNull() ?: return null
    val levelValue = Regex("[^\\s*/]+").find(comment.text)?.value ?: return null
    return LevelType.valueOf(levelValue)
  }

  private fun getUClassAtCaret(): UClass? {
    val elementAtCaret = myFixture.elementAtCaret.toUElement() ?: return null
    // to make it work for both Kotlin and Java through UAST
    return elementAtCaret.getContainingUClass() ?: elementAtCaret.getParentOfType(strict = false)
  }

  private fun serviceLevelByAnnotationTest(testName: String) {
    myFixture.testHighlighting(testName, *serviceDeclarationPaths)

    val uClass = getUClassAtCaret() ?: error("No class found at the caret for $testName")
    val expectedLevel = getExpectedServiceLevel(uClass)

    val annotation = uClass.findAnnotation(Service::class.qualifiedName!!) ?: error("No Service annotation found for ${uClass.name}")
    val psiAnnotation = annotation.javaPsi!!
    val actualLevel = getLevelType(psiAnnotation, uClass.language)

    assertEquals("Incorrect service level for ${uClass.name}", expectedLevel, actualLevel)
  }

  private fun serviceLevelByClassTest(testName: String) {
    myFixture.testHighlighting(testName, *serviceDeclarationPaths)

    val uClass = getUClassAtCaret() ?: error("No class found at the caret for $testName")
    val expectedLevel = getExpectedServiceLevel(uClass)
    val actualLevel = getLevelType(project, uClass)

    assertEquals("Incorrect service level for ${uClass.name}", expectedLevel, actualLevel)
  }

  private fun getPaths(subpath: String = "", extension: String? = null, nameFilter: (String) -> Boolean = { true }): Array<String> {
    return Path(testDataPath, subpath)
      .toFile()
      .walk()
      .filter {
        it.isFile && nameFilter(it.name) && extension?.let { ext -> it.extension == ext } ?: true
      }
      .map { it.relativeTo(File(testDataPath)).toString() }
      .toList()
      .toTypedArray()
  }

}
