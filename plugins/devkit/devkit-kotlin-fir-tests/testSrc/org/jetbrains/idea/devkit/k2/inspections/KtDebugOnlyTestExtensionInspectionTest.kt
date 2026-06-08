// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.DebugOnlyTestExtensionInspection
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/debugOnlyTestExtension")
class KtDebugOnlyTestExtensionInspectionTest : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(DebugOnlyTestExtensionInspection())
    myFixture.addClass(
      """
          package org.junit.jupiter.api.extension;
          public interface Extension {}
          """.trimIndent()
    )
    myFixture.addClass(
      """
          package org.junit.jupiter.api.extension;
          import java.lang.annotation.Retention;
          import java.lang.annotation.RetentionPolicy;
          @Retention(RetentionPolicy.RUNTIME)
          public @interface ExtendWith {
            Class<? extends Extension>[] value();
          }
          """.trimIndent()
    )
    myFixture.addClass(
      """
          package com.intellij.ide.starter.junit5;
          import org.junit.jupiter.api.extension.Extension;
          public class RemoteDevRun implements Extension {}
          """.trimIndent()
    )
    myFixture.addClass(
      """
          package com.intellij.ide.starter.extended.engine.junit5;
          import org.junit.jupiter.api.extension.Extension;
          public class UseInstaller implements Extension {}
          """.trimIndent()
    )
    myFixture.addClass(
      """
          package com.example;
          import org.junit.jupiter.api.extension.Extension;
          public class SomeValidExt implements Extension {}
          """.trimIndent()
    )
  }

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/debugOnlyTestExtension"

  override fun getFileExtension(): String = "kt"

  fun testHighlightsUseInstallerOnClass() {
    doTest()
  }

  fun testHighlightsRemoteDevRunOnMethod() {
    doTest()
  }

  fun testNoHighlightForRegularExtension() {
    doTest()
  }

  fun testQuickFixRemovesSingleLiteralAnnotationAndImports() {
    doTest("Remove @ExtendWith('UseInstaller')")
  }

  fun testQuickFixRemovesOnlyOffendingLiteralWhenMultiple() {
    doTest("Remove @ExtendWith('UseInstaller')")
  }

  fun testIgnoreFixAddsSuppressAnnotation() {
    doTest("Ignore 'Debug-only JUnit test extension must not be committed'")
  }
}
