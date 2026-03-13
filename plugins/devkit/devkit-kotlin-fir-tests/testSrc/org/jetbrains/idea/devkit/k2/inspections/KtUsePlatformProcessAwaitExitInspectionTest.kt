// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.UsePlatformProcessAwaitExitInspection
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/usePlatformProcessAwaitExit")
class KtUsePlatformProcessAwaitExitInspectionTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/usePlatformProcessAwaitExit"
  }

  @Throws(Exception::class)
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
    myFixture.enableInspections(UsePlatformProcessAwaitExitInspection())
    myFixture.addClass("""
      package java.util.concurrent;
      public class CompletableFuture<T> {}
      """.trimIndent())
    myFixture.addClass("""
      package java.util.concurrent;
      public enum TimeUnit {
        MILLISECONDS
      }
      """.trimIndent())
    myFixture.addClass("""
      package java.lang;
      import java.io.*;
      import java.util.concurrent.CompletableFuture;
      import java.util.concurrent.TimeUnit;
      public abstract class Process {
        public abstract int waitFor() throws InterruptedException;
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {}
        public abstract int exitValue();
        public CompletableFuture<Process> onExit() {}
      }
      """.trimIndent())
    myFixture.addFileToProject(
      "com/intellij/util/io/process.kt", """
      package com.intellij.util.io
      suspend fun Process.awaitExit(): Int {
        return 0
      }
      """.trimIndent()
    )
  }

  fun testUsePlatformProcessAwaitExit() {
    myFixture.testHighlighting(getTestName(false) + ".kt")
  }
}
