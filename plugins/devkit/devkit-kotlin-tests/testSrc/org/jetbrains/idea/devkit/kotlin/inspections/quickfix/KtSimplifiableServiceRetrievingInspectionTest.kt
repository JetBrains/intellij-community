// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.SimplifiableServiceRetrievingInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/simplifiableServiceRetrieving")
internal class KtSimplifiableServiceRetrievingInspectionTest : SimplifiableServiceRetrievingInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/simplifiableServiceRetrieving/"

  override fun getFileExtension() = "kt"

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
    doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

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
    doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyService", "getInstance"))
  }

  fun testTooGenericGetInstanceReturnType() {
    doTest()
  }

  fun testPropertyWithGetterRetrievingService() {
    doTest()
  }

  fun testNullableGetInstanceMethod() {
    doTest()
  }

  fun testReturnTypeHasTypeParam() {
    doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyAppService", "getInstance"))
  }

  fun testGetInstanceServicesKtMethodsNoWarnings() {
    doTestWithServicesKt()
  }

  fun testServicesKtMethodsCallsNoWarnings() {
    doTestWithServicesKt()
  }

  fun testServiceKtMethods() {
    doTestWithServiceKt()
  }

  fun testSafeCall() {
    doTestWithServicesKt()
  }

  fun testJvmName() {
    myFixture.addFileToProject(
      "JvmPlatformAnnotations.kt",
      //language=kotlin
      """
        package kotlin.jvm

        actual annotation class JvmName(actual val name: String)
      """
    )
    doTest(DevKitBundle.message("inspection.simplifiable.service.retrieving.replace.with", "MyAppService", "getInstance"))
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
    doTest()
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
    doTest()
  }
}
