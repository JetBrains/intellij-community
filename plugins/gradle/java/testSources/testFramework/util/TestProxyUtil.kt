// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter
import com.intellij.openapi.util.text.StringUtil

val AbstractTestProxy.consoleText: String
  get() {
    val printer = MockPrinter()
    printer.setShowHyperLink(true)
    printOn(printer)
    val consoleText = printer.allOut
    return StringUtil.convertLineSeparators(consoleText)
  }