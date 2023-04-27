// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.RetrievingServiceInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/retrievingService")
internal class KtRetrievingServiceInspectionTest : RetrievingServiceInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/retrievingService/"

  override fun getFileExtension() = "kt"

  fun testAppLevelServiceAsProjectLevel() {
    doTest()
  }

  fun testProjectLevelServiceAsAppLevel() {
    doTest()
  }

  fun testReplaceWithGetInstanceApplicationLevel() {
    myFixture.addClass(
      //language=java
      """
      import com.intellij.openapi.application.ApplicationManager;
      import com.intellij.openapi.components.Service;

      @Service(Service.Level.APP)
      public final class MyService {
        public static MyService getInstance() {
          return ApplicationManager.getApplication().getService(MyService.class);
        }
      }
    """)
    doTest(DevKitBundle.message("inspection.retrieving.service.replace.with", "MyService", "getInstance"))
  }

  fun testReplaceWithGetInstanceProjectLevel() {
    myFixture.addClass(
      //language=java
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
    doTest(DevKitBundle.message("inspection.retrieving.service.replace.with", "MyService", "getInstance"))
  }
}
