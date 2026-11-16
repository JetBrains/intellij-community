// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.util.function.Supplier
import org.jetbrains.idea.devkit.inspections.UseIntelliJVirtualThreadsInspection

/**
 * Creates the project of a [UseIntelliJVirtualThreadsInspection] test class with the mock JDK 21.
 * Declare it in a static field or in a companion object.
 */
fun useIntelliJVirtualThreadsProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(DefaultLightProjectDescriptor(Supplier { IdeaTestUtil.getMockJdk21() }))

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field. Give it the project of [useIntelliJVirtualThreadsProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [UseIntelliJVirtualThreadsInspection]. The project has the stub of `IntelliJVirtualThreads`.
 */
@JvmOverloads
fun useIntelliJVirtualThreadsFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("useIntelliJVirtualThreadsFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(UseIntelliJVirtualThreadsInspection())
  fixture.addClass(
    """package com.intellij.concurrency.virtualThreads;
      |public final class IntelliJVirtualThreads { 
      |  private IntelliJVirtualThreads() {}  
      |  public static java.lang.Thread.Builder ofVirtual() { 
      |    return null;
      |  }
      |}""".trimMargin()
  )
  initialized(devKit) {}
}
