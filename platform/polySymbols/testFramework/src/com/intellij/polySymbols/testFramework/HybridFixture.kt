// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * Creates a [HybridFixture]. It is the JUnit 5 replacement of the JUnit 3 base [HybridTestCase].
 * Declare the result as an instance property, so that each test gets its own fixture.
 *
 * [testDataPath] replaces an override of `HybridTestCase.getTestDataPath()`.
 * [mode] replaces the mode of [HybridTestCase]. The generic [hybridFixture] describes the project of each mode.
 */
@TestOnly
@JvmOverloads
fun hybridFixture(
  testDataPath: String,
  mode: HybridTestMode = HybridTestMode.BasePlatform,
): TestFixture<HybridFixture> =
  hybridFixture({ mode }) { HybridFixture(it, testDataPath) }

/**
 * Creates a sub-fixture of [HybridFixture] with [create].
 * Each sub-fixture factory calls this function, so that the setup of each fixture in the chain runs once.
 *
 * The fixture creates its [CodeInsightTestFixture] for the [mode], as [HybridTestCase] does:
 * - [HybridTestMode.BasePlatform] uses the light project of `BasePlatformTestCase`, with the files in the `temp:///src` source root.
 *   The light project stays open between the tests, as in JUnit 3.
 * - [HybridTestMode.CodeInsightFixture] replaces the heavy project of `CodeInsightFixtureTestCase`.
 *   Each test gets a new project, and the module uses the project directory as its root.
 *
 * The fixture reads [mode] when it initializes, so [mode] can read a parameter of a `@ParameterizedClass`.
 */
@TestOnly
fun <T : HybridFixture> hybridFixture(
  mode: () -> HybridTestMode,
  create: (HybridFixtureContext) -> T,
): TestFixture<T> = testFixture("hybridFixture") { context ->
  val testMode = mode()
  val extensionContext = context.extensionContext
  val testMethodName = extensionContext.requiredTestMethod.name
  val fixture = when (testMode) {
    HybridTestMode.BasePlatform -> lightCodeInsightFixture(testMethodName).init()
    HybridTestMode.CodeInsightFixture -> codeInsightFixture(codeInsightProjectFixture()).init()
  }
  val result = create(HybridFixtureContext(fixture, testMode, extensionContext.requiredTestClass, testMethodName))
  result.runSetUp()
  initialized(result) {}
}

/**
 * Creates the [CodeInsightTestFixture] of `BasePlatformTestCase` on the light project, as [HybridTestCase] does in its light mode.
 */
private fun lightCodeInsightFixture(testMethodName: String): TestFixture<CodeInsightTestFixture> = testFixture("lightCodeInsightFixture") {
  val factory = IdeaTestFixtureFactory.getFixtureFactory()
  val projectFixture = factory.createLightFixtureBuilder(null, PlatformTestUtil.getTestName(testMethodName, false)).fixture
  val tempDirFixture = IdeaTestExecutionPolicy.current()?.createTempDirTestFixture() ?: LightTempDirTestFixtureImpl(true)
  val fixture = factory.createCodeInsightFixture(projectFixture, tempDirFixture)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

/**
 * The state that [hybridFixture] gives to the constructor of a [HybridFixture].
 */
@TestOnly
class HybridFixtureContext internal constructor(
  val fixture: CodeInsightTestFixture,
  val mode: HybridTestMode,
  val testClass: Class<*>,
  val testMethodName: String,
)

/**
 * The state of [HybridTestCase] for a JUnit 5 test.
 * Call a member from a background thread. A member takes the read actions and the EDT calls that it needs.
 * The constructor sets [testDataPath] on [fixture].
 */
@TestOnly
open class HybridFixture(context: HybridFixtureContext, testDataPath: String) {
  val fixture: CodeInsightTestFixture = context.fixture
  val mode: HybridTestMode = context.mode
  val testClass: Class<*> = context.testClass
  val testMethodName: String = context.testMethodName

  init {
    fixture.testDataPath = testDataPath
  }

  val project: Project get() = fixture.project
  val module: Module get() = fixture.module
  val testRootDisposable: Disposable get() = fixture.testRootDisposable
  val testDataPath: String get() = fixture.testDataPath

  /**
   * Returns the name of the test method without the `test` prefix, as `UsefulTestCase.getTestName` does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter)

  /**
   * Runs the setup of the fixture. [hybridFixture] calls it once, off the EDT, before the `@BeforeEach` methods of the test.
   * An override calls `super.setUp()` first.
   */
  protected open fun setUp() {
  }

  internal fun runSetUp() {
    setUp()
  }
}
