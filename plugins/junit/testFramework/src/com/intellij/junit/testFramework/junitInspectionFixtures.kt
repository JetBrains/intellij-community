// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JUnitInspectionFixtures")

package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.HamcrestAssertionsConverterInspection
import com.intellij.execution.junit.codeInspection.JUnit3SuperTearDownInspection
import com.intellij.execution.junit.codeInspection.JUnit4ConverterInspection
import com.intellij.execution.junit.codeInspection.JUnit5AssertionsConverterInspection
import com.intellij.execution.junit.codeInspection.JUnit5ConverterInspection
import com.intellij.execution.junit.codeInspection.JUnitAssertEqualsMayBeAssertSameInspection
import com.intellij.execution.junit.codeInspection.JUnitAssertEqualsOnArrayInspection
import com.intellij.execution.junit.codeInspection.JUnitIgnoredTestInspection
import com.intellij.execution.junit.codeInspection.JUnitMalformedDeclarationInspection
import com.intellij.execution.junit.codeInspection.JUnitMixedFrameworkInspection
import com.intellij.execution.junit.codeInspection.TestCaseWithMultipleRunnersInspection
import com.intellij.junit.testFramework.MavenTestLib.HAMCREST
import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.java.JavaResourceRootType

/**
 * Creates the project of a JUnit test class with the language level [com.intellij.pom.java.LanguageLevel.HIGHEST] and [libraries].
 * Declare it in a companion object or in a static field. The JUnit test bases of this module use the libraries that their KDoc names.
 */
@TestOnly
fun junitProjectFixture(vararg libraries: MavenTestLib): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(JUnitProjectDescriptor(HIGHEST, *libraries))

/**
 * Creates the fixture of a [HamcrestAssertionsConverterInspection] test. It replaces [HamcrestAssertionsConverterInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT4] and [HAMCREST].
 */
@TestOnly
fun hamcrestAssertionsConverterInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<HamcrestAssertionsConverterInspection>> =
  jvmInspectionFixture(projectFixture) { HamcrestAssertionsConverterInspection() }

/**
 * Creates the fixture of a [JUnit3SuperTearDownInspection] test. It replaces [JUnit3SuperTearDownInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT3].
 */
@TestOnly
fun junit3SuperTearDownInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnit3SuperTearDownInspection>> =
  jvmInspectionFixture(projectFixture) { JUnit3SuperTearDownInspection() }

/**
 * Creates the fixture of a [JUnit4ConverterInspection] test. It replaces [JUnit4ConverterInspectionTestBase].
 * The fixture sets the level of the inspection to a warning.
 * Give it the project of [junitProjectFixture] with [JUNIT3] and [JUNIT4].
 */
@TestOnly
fun junit4ConverterInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnit4ConverterInspection>> = testFixture("junit4ConverterInspectionFixture") {
  val converter = jvmInspectionFixture(projectFixture) { JUnit4ConverterInspection() }.init()
  converter.enableWarnings()
  initialized(converter) {}
}

/**
 * Creates the fixture of a [JUnit5AssertionsConverterInspection] test. It replaces [JUnit5AssertionsConverterInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT4], [JUNIT5] and [HAMCREST].
 */
@TestOnly
fun junit5AssertionsConverterInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnit5AssertionsConverterInspection>> =
  jvmInspectionFixture(projectFixture) { JUnit5AssertionsConverterInspection() }

/**
 * Creates the fixture of a [JUnit5ConverterInspection] test. It replaces [JUnit5ConverterInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT4] and [JUNIT5].
 */
@TestOnly
fun junit5ConverterInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnit5ConverterInspection>> =
  jvmInspectionFixture(projectFixture) { JUnit5ConverterInspection() }

/**
 * Creates the fixture of a [JUnitAssertEqualsMayBeAssertSameInspection] test. It replaces [JUnitAssertEqualsMayBeAssertSameInspectionTestBase].
 * The fixture adds the final class `A` with two constants.
 * Give it the project of [junitProjectFixture] with [JUNIT4].
 */
@TestOnly
fun junitAssertEqualsMayBeAssertSameInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnitAssertEqualsMayBeAssertSameInspection>> =
  testFixture("junitAssertEqualsMayBeAssertSameInspectionFixture") {
    val assertSame = jvmInspectionFixture(projectFixture) { JUnitAssertEqualsMayBeAssertSameInspection() }.init()
    @Suppress("InstantiationOfUtilityClass")
    assertSame.addClass("""
      final class A {
        public static final A a = new A();
        public static final A b = a;
      }
    """.trimIndent())
    initialized(assertSame) {}
  }

/**
 * Creates the fixture of a [JUnitAssertEqualsOnArrayInspection] test. It replaces [JUnitAssertEqualsOnArrayInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT5].
 */
@TestOnly
fun junitAssertEqualsOnArrayInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnitAssertEqualsOnArrayInspection>> =
  jvmInspectionFixture(projectFixture) { JUnitAssertEqualsOnArrayInspection() }

/**
 * Creates the fixture of a [JUnitIgnoredTestInspection] test. It replaces [JUnitIgnoredTestInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT4] and [JUNIT5].
 */
@TestOnly
fun junitIgnoredTestInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnitIgnoredTestInspection>> =
  jvmInspectionFixture(projectFixture) { JUnitIgnoredTestInspection() }

/**
 * Creates the fixture of a [JUnitMixedFrameworkInspection] test. It replaces [JUnitMixedFrameworkInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT3], [JUNIT4] and [JUNIT5].
 */
@TestOnly
fun junitMixedFrameworkInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<JUnitMixedFrameworkInspection>> =
  jvmInspectionFixture(projectFixture) { JUnitMixedFrameworkInspection() }

/**
 * Creates the fixture of a [TestCaseWithMultipleRunnersInspection] test. It replaces [TestCaseWithMultipleRunnersInspectionTestBase].
 * Give it the project of [junitProjectFixture] with [JUNIT4].
 */
@TestOnly
fun testCaseWithMultipleRunnersInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<TestCaseWithMultipleRunnersInspection>> =
  jvmInspectionFixture(projectFixture) { TestCaseWithMultipleRunnersInspection() }

/**
 * The fixture of a [JUnitMalformedDeclarationInspection] test. It is the JUnit 5 form of [JUnitMalformedDeclarationInspectionTestBase].
 * Create it with [junitMalformedDeclarationInspectionFixture], and give it the project of [junitProjectFixture] with the libraries of the class.
 */
@TestOnly
class JUnitMalformedDeclarationInspectionFixture internal constructor(
  base: JvmInspectionFixture<JUnitMalformedDeclarationInspection>,
) : JvmInspectionFixture<JUnitMalformedDeclarationInspection>(base.fixture, base.inspection) {
  /**
   * Registers the JUnit 5 extension services file `META-INF/services` with [text] in a new resource root `resources`.
   * The resource root changes the module, so the next test gets a new module.
   */
  fun addAutomaticExtension(text: String) {
    val servicesDir = createServiceResourceDir()
    WriteCommandAction.runWriteCommandAction(project) {
      servicesDir.createFile(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION).also { file -> file.writeText(text) }
    }
  }

  private fun createServiceResourceDir(): VirtualFile {
    return WriteCommandAction.runWriteCommandAction(project, Computable {
      val resourceRoot = tempDirFixture.findOrCreateDir("resources").also { root ->
        PsiTestUtil.addSourceRoot(module, root, JavaResourceRootType.RESOURCE)
      }
      val metaInf = resourceRoot.createDirectory("META-INF")
      metaInf.createDirectory("services")
    })
  }
}

/**
 * Creates a [JUnitMalformedDeclarationInspectionFixture] for one test.
 */
@TestOnly
fun junitMalformedDeclarationInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JUnitMalformedDeclarationInspectionFixture> = testFixture("junitMalformedDeclarationInspectionFixture") {
  initialized(JUnitMalformedDeclarationInspectionFixture(jvmInspectionFixture(projectFixture) { JUnitMalformedDeclarationInspection() }.init())) {}
}
