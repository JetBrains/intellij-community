// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class IncorrectCancellationExceptionHandlingInspectionTestBase : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(IncorrectCancellationExceptionHandlingInspection())
    myFixture.addClass("""
        package com.intellij.openapi.progress;
        public class ProcessCanceledException extends RuntimeException {}
        """.trimIndent())
    myFixture.addClass("""
        package com.intellij.openapi.diagnostic;
        public abstract class Logger {
          public static Logger getInstance(Class<?> cl) {return null;}
          public static Logger getInstance(String category) {return null;}
          public void info(Throwable t) {}
          public abstract void info(String message);
          public abstract void info(String message, Throwable t);
          public void error(String message) {}
          public void error(String message, Throwable t) {}
          public void error(Throwable t) {}
        }
        """.trimIndent())
  }

  protected fun doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension())
  }

  protected abstract fun getFileExtension(): String

}
