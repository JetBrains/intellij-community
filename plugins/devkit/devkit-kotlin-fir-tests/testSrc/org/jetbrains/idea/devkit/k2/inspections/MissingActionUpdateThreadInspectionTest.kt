// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.MissingActionUpdateThread
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingActionUpdateThread")
@TestApplication
class MissingActionUpdateThreadInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/missingActionUpdateThread")

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(MissingActionUpdateThread::class.java)
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "public interface ActionUpdateThreadAware {" +
                       "  default ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }" +
                       "}")
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "import com.intellij.openapi.actionSystem.ActionUpdateThread;" +
                       "public class AnAction implements ActionUpdateThreadAware {" +
                       "  public void update(AnActionEvent event);" +
                       "  @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }" +
                       "}")
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "public class AnActionEvent {}")
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "public enum ActionUpdateThread { EDT, BGT }")
    myFixture.addClass("package kotlin.jvm;" +
                       "public @interface JvmDefault {}")
  }

  private fun doTest() {
    myFixture.testHighlighting(myFixture.getTestName(false) + ".kt")
  }

  @Test
  fun testAction(): Unit = doTest()
  @Test
  fun testActionUpdateAware(): Unit = doTest()
}