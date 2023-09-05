// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.testFramework.LightProjectDescriptor

class CodeBlockGenerationJava14Test extends CodeBlockGenerationBaseTest {

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    // text blocks are not yet supported there
    return JAVA_14;
  }

  void testMultilineStringOldJava() {
    doTest()
  }
}
