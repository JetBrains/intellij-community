// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.DebugOnlyTestExtensionInspection
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/debugOnlyTestExtension")
@TestApplication
class KtDebugOnlyTestExtensionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val devKit by lightDevKitInspectionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/debugOnlyTestExtension")
  private val myFixture get() = devKit.fixture

  @BeforeEach
  fun setUp() {
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

  @Test
  fun testHighlightsUseInstallerOnClass() {
    devKit.doTest()
  }

  @Test
  fun testHighlightsRemoteDevRunOnMethod() {
    devKit.doTest()
  }

  @Test
  fun testNoHighlightForRegularExtension() {
    devKit.doTest()
  }

  @Test
  fun testQuickFixRemovesSingleLiteralAnnotationAndImports() {
    devKit.doTest("Remove @ExtendWith('UseInstaller')")
  }

  @Test
  fun testQuickFixRemovesOnlyOffendingLiteralWhenMultiple() {
    devKit.doTest("Remove @ExtendWith('UseInstaller')")
  }

  @Test
  fun testIgnoreFixAddsSuppressAnnotation() {
    devKit.doTest("Ignore 'Debug-only JUnit test extension must not be committed'")
  }
}
