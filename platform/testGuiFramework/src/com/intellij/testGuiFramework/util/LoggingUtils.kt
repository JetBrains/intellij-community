// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.impl.GuiTestCase

val GuiTestCase.logger: Logger
  get() = Logger.getInstance(this::class.java)

fun GuiTestCase.logInfo(message: String) {
  println(message)
  logger.info(message)
}

fun GuiTestCase.logStartTest(testName: String) {
  logInfo("----------------->>> Test `$testName` started")
}

fun GuiTestCase.logEndTest(testName: String) {
  logInfo("<<<----------------- Test `$testName` finished")
}

fun GuiTestCase.logTestStep(step: String) {
  logInfo("Test step`$step` going to execute")
}

fun GuiTestCase.logUIStep(step: String) {
  logInfo("UI step`$step` going to execute")
}

fun GuiTestCase.logError(error: String) {
  println(error)
  logger.error(error)
}
