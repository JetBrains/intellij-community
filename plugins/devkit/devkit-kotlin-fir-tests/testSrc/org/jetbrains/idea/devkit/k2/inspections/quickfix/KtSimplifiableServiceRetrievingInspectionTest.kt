// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.simplifiableServiceRetrievingFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/simplifiableServiceRetrieving")
@TestApplication
internal class KtSimplifiableServiceRetrievingInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val simplifiable by simplifiableServiceRetrievingFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/simplifiableServiceRetrieving/")
  private val myFixture get() = simplifiable.fixture

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    }
  }

  @Test
  fun testReplaceWithGetInstanceApplicationLevel() {
    myFixture.addClass(
      """
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.components.Service;

      @Service
      public final class MyService {
        public static MyService getInstance() {
          return ApplicationManager.getApplication().getService(MyService.class);
        }
      }
      """)
    simplifiable.doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  @Test
  fun testReplaceWithGetInstanceProjectLevel() {
    myFixture.addClass(
      """
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.components.Service;
      import com.intellij.openapi.project.Project;

      @Service(Service.Level.PROJECT)
      public final class MyService {
        public static MyService getInstance(Project project) {
          return project.getService(MyService.class);
        }
      }
      """)
    simplifiable.doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  @Test
  fun testTooGenericGetInstanceReturnType() {
    simplifiable.doTest()
  }

  @Test
  fun testPropertyWithGetterRetrievingService() {
    simplifiable.doTest()
  }

  @Test
  fun testNullableGetInstanceMethod() {
    simplifiable.doTest()
  }

  @Test
  fun testReturnTypeHasTypeParam() {
    simplifiable.doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyAppService", "getInstance"))
  }

  @Test
  fun testGetInstanceServicesKtMethodsNoWarnings() {
    doTestWithServicesKt()
  }

  @Test
  fun testServicesKtMethodsCallsNoWarnings() {
    doTestWithServicesKt()
  }

  @Test
  fun testServiceKtMethods() {
    doTestWithServiceKt()
  }

  @Test
  fun testSafeCall() {
    doTestWithServicesKt()
  }

  @Test
  fun testJvmName() {
    myFixture.addFileToProject(
      "JvmPlatformAnnotations.kt",
      //language=kotlin
      """
        package kotlin.jvm

        actual annotation class JvmName(actual val name: String)
      """
    )
    simplifiable.doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyAppService", "getInstance"))
  }

  @Test
  fun testReturnExpressionOfNullableMethod() {
    simplifiable.doTest()
  }

  private fun doTestWithServicesKt() {
    myFixture.configureByText(
      "services.kt",
      //language=kotlin
      """
      @file:Suppress("UnusedReceiverParameter")

      package com.intellij.openapi.components

      inline fun <reified T : Any> ComponentManager.service(): T {}
      inline fun <reified T : Any> ComponentManager.serviceIfCreated(): T? {}
      inline fun <reified T : Any> ComponentManager.serviceOrNull(): T? {}
    """)
    simplifiable.doTest()
  }

  private fun doTestWithServiceKt() {
    myFixture.addFileToProject(
      "service.kt",
      //language=kotlin
      """
      package com.intellij.openapi.components

      inline fun <reified T : Any> service(): T {}
      inline fun <reified T : Any> serviceIfCreated(): T? {}
      inline fun <reified T : Any> serviceOrNull(): T? {}
    """)
    simplifiable.doTest()
  }
}
