// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createDirectory
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.ESListenerLeakTracker
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

@TestApplication
abstract class GradleBaseTestCase {

  private lateinit var listenerLeakTracker: ESListenerLeakTracker
  private lateinit var reloadLeakTracker: OperationLeakTracker

  lateinit var testDisposable: Disposable

  private lateinit var sdkFixture: SdkTestFixture
  private lateinit var fileFixture: TempDirTestFixture

  lateinit var testRoot: VirtualFile

  val gradleJvm: String
    get() = sdkFixture.getSdk().name

  val gradleVersion: GradleVersion
    get() = GradleVersion.current()

  @BeforeEach
  fun setUpGradleBaseTestCase(testInfo: TestInfo) {
    listenerLeakTracker = ESListenerLeakTracker()
    listenerLeakTracker.setUp()

    reloadLeakTracker = OperationLeakTracker { getGradleProjectReloadOperation(it) }
    reloadLeakTracker.setUp()

    testDisposable = Disposer.newDisposable()

    sdkFixture = GradleTestFixtureFactory.getFixtureFactory().createGradleJvmTestFixture(gradleVersion)
    sdkFixture.setUp()

    fileFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    fileFixture.setUp()
    runWriteActionAndWait {
      testRoot = fileFixture.findOrCreateDir(testInfo.testClass.get().simpleName)
        .createDirectory(testInfo.testMethod.get().name)
    }

    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
  }

  @AfterEach
  fun tearDownGradleBaseTestCase() {
    runAll(
      { fileFixture.tearDown() },
      { sdkFixture.tearDown() },
      { Disposer.dispose(testDisposable) },
      { reloadLeakTracker.tearDown() },
      { listenerLeakTracker.tearDown() }
    )
  }

  suspend fun <R> awaitAnyGradleProjectReload(wait: Boolean = true, action: suspend () -> R): R {
    if (!wait) {
      return action()
    }
    return reloadLeakTracker.withAllowedOperationAsync(1) {
      org.jetbrains.plugins.gradle.testFramework.util.awaitAnyGradleProjectReload {
        action()
      }
    }
  }
}