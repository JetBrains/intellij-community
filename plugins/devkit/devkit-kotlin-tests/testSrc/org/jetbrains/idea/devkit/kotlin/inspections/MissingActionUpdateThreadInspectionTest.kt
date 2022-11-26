// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.MissingActionUpdateThread
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingActionUpdateThread")
class MissingActionUpdateThreadInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/missingActionUpdateThread"
  }

  override fun setUp() {
    super.setUp()
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
    myFixture.testHighlighting(getTestName(false) + ".kt")
  }

  fun testAction() = doTest()
  fun testActionUpdateAware() = doTest()
}