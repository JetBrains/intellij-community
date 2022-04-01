// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class SpockFormattingTest extends GroovyFormatterTestCase {

  LightProjectDescriptor projectDescriptor = SpockTestBase.SPOCK_PROJECT
  final String basePath = TestUtils.testDataPath + "groovy/formatter/"

  void testSpockTableWithStringComment() throws Throwable { doTest() }

  void testSpockTableWithComments() throws Throwable { doTest() }

  void testSpockTableWithFullwidthCharacters() throws Throwable { doTest() }

  void testSpockTableWithLongTableParts() throws Throwable { doTest() }

  void testSpockTableSeparatedByUnderscores() throws Throwable { doTest() }

  void testSpockTableWithUndefinedLabel() throws Throwable { doTest() }

  void doTest() {
    def (String before, String after) = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    checkFormatting(before, StringUtil.trimEnd(after, "\n"))
  }
}
