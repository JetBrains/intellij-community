// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.logging;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.logging.LoggerInitializedWithForeignClassInspection;

public class LoggerInitializedWithForeignClassFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "Logging.class");
    myFixture.addClass("package org.apache.logging.log4j; public class LogManager { public static Logger getLogger(Class clazz) { return null; }}");
    myFixture.enableInspections(new LoggerInitializedWithForeignClassInspection());
    myRelativePath = "logging/logger_initialized_with_foreign_class";
  }

  public void testLog4J2() { doTest(); }
}
