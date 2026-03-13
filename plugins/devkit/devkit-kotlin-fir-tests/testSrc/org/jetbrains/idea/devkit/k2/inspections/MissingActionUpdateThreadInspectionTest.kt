// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.MissingActionUpdateThread
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/missingActionUpdateThread")
class MissingActionUpdateThreadInspectionTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {

  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/missingActionUpdateThread"
  }

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
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