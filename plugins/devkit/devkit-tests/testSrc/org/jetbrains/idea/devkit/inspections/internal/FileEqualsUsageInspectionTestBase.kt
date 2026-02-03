// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase

abstract class FileEqualsUsageInspectionTestBase : PluginModuleTestCase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(FileEqualsUsageInspection())
    myFixture.addClass("package com.intellij.openapi.util.io; public class FileUtil {}")
  }

  protected fun doTest(methodExpressionText: String, highlightError: Boolean) {
    val expectedMethodExpression = getMethodExpressionText(methodExpressionText, highlightError)
    doTest(expectedMethodExpression)
  }

  protected abstract fun doTest(expectedMethodExpression: String)

  protected fun getMethodExpressionText(methodExpressionText: String, highlightError: Boolean): String {
    return if (highlightError) {
      val methodName = StringUtil.substringBefore(methodExpressionText, "(")
      val methodParams = StringUtil.substringAfter(methodExpressionText, methodName!!)
      """<warning descr="${errorMessage()}">$methodName</warning>$methodParams"""
    }
    else {
      methodExpressionText
    }
  }

  protected fun getOperatorText(operatorText: String, highlightError: Boolean): String {
    return if (highlightError) {
      """<warning descr="${errorMessage()}">$operatorText</warning>"""
    }
    else {
      operatorText
    }
  }

  private fun errorMessage(): @Nls String? =
    DevKitBundle.message("inspections.file.equals.method")

}
